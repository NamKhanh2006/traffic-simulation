package com.myteam.traffic.controller;

import com.myteam.traffic.behavior.common.Action;
import com.myteam.traffic.behavior.DriverBehavior;
import com.myteam.traffic.context.RoadContext;
import com.myteam.traffic.light.TrafficLight;
import com.myteam.traffic.light.TrafficLightState;
import com.myteam.traffic.model.geometry.Position;
import com.myteam.traffic.model.geometry.Direction;
import com.myteam.traffic.model.infrastructure.*;
import com.myteam.traffic.model.infrastructure.intersection.Intersection;
import com.myteam.traffic.navigation.IntersectionNavigator;
import com.myteam.traffic.navigation.IntersectionPath;
import com.myteam.traffic.navigation.PathFollower;
import com.myteam.traffic.rule.TrafficRule;
import com.myteam.traffic.vehicle.*;
import com.myteam.traffic.vehicle.emergency.Ambulance;
import com.myteam.traffic.vehicle.emergency.FireTruck;

import java.util.*;
import java.util.function.Supplier;

public class TrafficController {

    // TĂNG LÊN 150.0 ĐỂ QUÉT ĐƯỢC CÁC VÒNG XUYẾN KHỔNG LỒ (Chống lỗi xóa nhầm xe)
    private static final double APPROACHING_THRESHOLD = 150.0;
    private static final double INITIAL_SPEED = 20.0;
    private static final double NORMAL_ACCEL  = 8.0;
    private static final double NORMAL_BRAKE  = 12.0;
    private static final double HARD_BRAKE    = 35.0;

    private final RoadNetwork network;
    private final IntersectionNavigator intersectionNavigator;
    private final PathFollower pathFollower = new PathFollower();
    private final Random random = new Random();

    private final List<Vehicle> vehicles = new ArrayList<>();
    private final List<TrafficLight> lights = new ArrayList<>();
    private final List<TrafficRule> globalRules = new ArrayList<>();

    public TrafficController(RoadNetwork network) {
        if (network == null) throw new IllegalArgumentException("Network không được null");
        this.network = network;
        this.intersectionNavigator = new IntersectionNavigator(network);
    }

    public void spawnVehicle(RoadSegment entrySegment, Lane lane, DriverBehavior behavior, VehicleType vehicleType) {
        Vehicle v = createVehicleByType(vehicleType, behavior);
        double startT = (lane.getDirection() == Lane.Direction.FORWARD) ? 0.0 : 1.0;
        v.placeOnSegment(entrySegment, lane, startT);
        v.setSpeed(INITIAL_SPEED);
        addVehicle(v);
    }

    public Vehicle createVehicleByType(VehicleType type, DriverBehavior behavior) {
        Position p = new Position(0, 0);
        Direction d = new Direction(0);
        return switch (type) {
            case CAR       -> new Car(p, d, behavior);
            case MOTORBIKE -> new Motorbike(p, d, behavior);
            case BICYCLE   -> new Bicycle(p, d, behavior);
            case AMBULANCE -> new Ambulance(p, d, behavior);
            case FIRETRUCK -> new FireTruck(p, d, behavior);
        };
    }

    public void addVehicle(Vehicle v) { if (v != null) vehicles.add(v); }
    public void addLight(TrafficLight light) { if (light != null) lights.add(light); }
    public void addRule(TrafficRule rule) { if (rule != null) globalRules.add(rule); }
    public List<TrafficRule> getGlobalRules() { return Collections.unmodifiableList(globalRules); }

    public void tick(double deltaTime) {
        vehicles.removeIf(v -> {
            if (v.getTravelMode() != TravelMode.ON_SEGMENT) return false;
            if (v.getCurrentLane() == null) return true;

            boolean reachedEnd = (v.getCurrentLane().getDirection() == Lane.Direction.FORWARD)
                    ? v.getSegmentProgress() >= 1.0
                    : v.getSegmentProgress() <= 0.0;

            return reachedEnd && findUpcomingIntersection(v, v.getPosition()) == null;
        });

        for (TrafficLight light : lights) light.tick();
        Map<Vehicle, Position> snapshot = takePositionSnapshot();
        Map<Vehicle, Double> pathProgressSnapshot = new HashMap<>();
        Map<Vehicle, Double> speedSnapshot = new HashMap<>();
        for (Vehicle v : vehicles) {
            pathProgressSnapshot.put(v, v.getPathProgress());
            speedSnapshot.put(v, v.getSpeed());
        }

        for (Vehicle v : new ArrayList<>(vehicles)) {
            autoSetPlannedExit(v);
            RoadContext ctx = buildContext(v, snapshot, pathProgressSnapshot, speedSnapshot, deltaTime);
            Action proposed = v.getBehavior().decideAction(v, ctx);
            Action action = isVehicleAllowed(v, proposed, ctx)
                    ? proposed
                    : v.getBehavior().handleRejection(v, ctx, proposed);

            executeAction(v, action, deltaTime);
            tryEnterIntersection(v);
            tryExitIntersection(v);
        }
    }

    private void autoSetPlannedExit(Vehicle v) {
        if (v.getTravelMode() != TravelMode.ON_SEGMENT) return;
        if (v.getPlannedExit() != PlannedExit.NONE) return;

        // Ngưỡng gần cuối đường (giảm xuống 0.7 để đặt hướng sớm hơn)
        boolean nearEnd = (v.getCurrentLane().getDirection() == Lane.Direction.FORWARD)
            ? v.getSegmentProgress() > 0.70
            : v.getSegmentProgress() < 0.30;

        if (!nearEnd) return;

        Intersection upcoming = findUpcomingIntersection(v, v.getPosition());
        if (upcoming == null) return;

        Lane currentLane = v.getCurrentLane();
        // Lấy danh sách các hướng được phép trên làn này
        Set<Lane.Movement> allowed = currentLane.getAllowedMovements();
        if (allowed.isEmpty()) return; // Không có hướng nào được phép → không đặt exit

        // Xây dựng danh sách PlannedExit khả dụng
        List<PlannedExit> possible = new ArrayList<>();
        if (allowed.contains(Lane.Movement.STRAIGHT)) possible.add(PlannedExit.STRAIGHT);
        if (allowed.contains(Lane.Movement.LEFT)) possible.add(PlannedExit.LEFT);
        if (allowed.contains(Lane.Movement.RIGHT)) possible.add(PlannedExit.RIGHT);
        if (allowed.contains(Lane.Movement.U_TURN)) possible.add(PlannedExit.U_TURN);
        // Có thể thêm U_TURN nếu cần (nhưng cần kiểm tra marking)

        if (possible.isEmpty()) return;

        // Chọn ngẫu nhiên trong các hướng được phép (có thể theo tỉ lệ)
        PlannedExit exit = possible.get(random.nextInt(possible.size()));
        v.setPlannedExit(exit);
    }

    private Map<Vehicle, Position> takePositionSnapshot() {
        Map<Vehicle, Position> snapshot = new HashMap<>();
        for (Vehicle v : vehicles) snapshot.put(v, v.getPosition());
        return Collections.unmodifiableMap(snapshot);
    }

    private RoadContext buildContext(Vehicle subject, Map<Vehicle, Position> snapshot, Map<Vehicle, Double> pathProgressSnapshot,
                                  Map<Vehicle, Double> speedSnapshot, double deltaTime) {
        TrafficLightState lightState = getCurrentLightState(subject);
        List<Vehicle> nearby = findNearbyVehicles(subject, snapshot);
        return new RoadContext.Builder()
                .subject(subject)
                .lightState(lightState)
                .currentLane(subject.getCurrentLane())
                .currentSegment(subject.getCurrentSegment())
                .nearbyVehicles(nearby)
                .positionSnapshot(snapshot)
                .pathProgressSnapshot(pathProgressSnapshot)
                .speedSnapshot(speedSnapshot)
                .deltaTime(deltaTime)
                .build();
    }

    private List<Vehicle> findNearbyVehicles(Vehicle subject, Map<Vehicle, Position> snapshot) {
        Position subjectPos = snapshot.getOrDefault(subject, subject.getPosition());
        RoadSegment subjectSeg = subject.getCurrentSegment();
        Intersection upcoming = findUpcomingIntersection(subject, subjectPos);
        Intersection current = subject.getCurrentIntersection();
        if (current == null) current = findCurrentIntersection(subjectPos);

        List<Vehicle> nearby = new ArrayList<>();
        for (Vehicle other : vehicles) {
            if (other == subject) continue;
            if (subjectSeg != null && other.getCurrentSegment() == subjectSeg) {
                nearby.add(other); continue;
            }
            Position otherPos = snapshot.getOrDefault(other, other.getPosition());
            if (upcoming != null && isAtIntersection(otherPos, upcoming)) {
                nearby.add(other); continue;
            }
            if (current != null && isAtIntersection(otherPos, current)) {
                nearby.add(other);
            }
        }
        return nearby;
    }

    private Intersection findUpcomingIntersection(Vehicle v, Position pos) {
        RoadSegment seg = v.getCurrentSegment();
        Lane lane = v.getCurrentLane();
        if (seg == null || lane == null) return null;

        double targetX = (lane.getDirection() == Lane.Direction.FORWARD) ? seg.getEndX() : seg.getStartX();
        double targetY = (lane.getDirection() == Lane.Direction.FORWARD) ? seg.getEndY() : seg.getStartY();

        if (pos.distanceTo(new Position(targetX, targetY)) > APPROACHING_THRESHOLD) return null;
        return network.findNearestIntersection(targetX, targetY, APPROACHING_THRESHOLD);
    }

    private Intersection findCurrentIntersection(Position pos) {
        for (Intersection inter : network.getIntersections()) {
            if (isAtIntersection(pos, inter)) return inter;
        }
        return null;
    }

    private boolean isAtIntersection(Position pos, Intersection inter) {
        double radius = inter.getRenderData().radius;
        return pos.distanceTo(new Position(inter.getCenterX(), inter.getCenterY())) <= radius;
    }

    public boolean isVehicleAllowed(Vehicle v, Action a, RoadContext ctx) {
        List<TrafficRule> allRules = new ArrayList<>(globalRules);
        allRules.addAll(ctx.getLocalRules());
        allRules.sort(Comparator.comparingInt(TrafficRule::getPriority).reversed());
        for (TrafficRule rule : allRules) {
            if (!rule.appliesTo(v)) continue;
            if (!rule.isAllowed(v, a, ctx)) return false;
        }
        return true;
    }

    private void executeAction(Vehicle v, Action action, double deltaTime) {
        if (v.getTravelMode() == TravelMode.ON_INTERSECTION_PATH) {
            executeOnIntersectionPath(v, action, deltaTime);
        } else {
            executeOnSegment(v, action, deltaTime);
        }
    }

    private void executeOnSegment(Vehicle v, Action action, double deltaTime) {
        RoadSegment seg = v.getCurrentSegment();
        if (seg == null) return;

        switch (action) {
            case ACCELERATE -> {
                v.setSpeed(Math.min(v.getMaxSpeed(), v.getSpeed() + NORMAL_ACCEL * deltaTime));
                advanceOnSegment(v, deltaTime);
            }
            case MOVE_FORWARD -> advanceOnSegment(v, deltaTime);
            case SLOW_DOWN -> {
                v.setSpeed(Math.max(0, v.getSpeed() - NORMAL_BRAKE * deltaTime));
                if (v.getSpeed() > 0) advanceOnSegment(v, deltaTime);
            }
            case STOP -> {
                v.setSpeed(Math.max(0, v.getSpeed() - HARD_BRAKE * deltaTime));
                if (v.getSpeed() > 0) advanceOnSegment(v, deltaTime);
            }
            case CHANGE_LANE -> {
                if (v.getLaneChangeProgress() >= 1.0) {
                    int currentIdx = v.getCurrentLane().getIndex();
                    int newIdx = -1;
                    // Thử sang phải trước
                    if (currentIdx + 1 < seg.getLanes().size() &&
                            seg.getLanes().get(currentIdx + 1).getDirection() == v.getCurrentLane().getDirection()) {
                        newIdx = currentIdx + 1;
                    }
                    // Nếu không thể sang phải, thử sang trái
                    else if (currentIdx - 1 >= 0 &&
                            seg.getLanes().get(currentIdx - 1).getDirection() == v.getCurrentLane().getDirection()) {
                        newIdx = currentIdx - 1;
                    }
                    if (newIdx != -1 && isLaneSafeToEnter(v, seg, newIdx, 5.0)) {
                        v.changeLaneIndex(newIdx);
                    }
                }
                advanceOnSegment(v, deltaTime);
            }
            case OVERTAKE -> {
                v.setSpeed(Math.min(v.getMaxSpeed(), v.getSpeed() + NORMAL_ACCEL * deltaTime));
                if (v.getLaneChangeProgress() >= 1.0) {
                    int leftIdx = v.getCurrentLane().getIndex() - 1;
                    if (leftIdx >= 0 &&
                            seg.getLanes().get(leftIdx).getDirection() == v.getCurrentLane().getDirection() &&
                            isLaneSafeToEnter(v, seg, leftIdx, 5.0)) {
                        v.changeLaneIndex(leftIdx);
                    }
                }
                advanceOnSegment(v, deltaTime);
            }
            case HONK -> { v.honk(); advanceOnSegment(v, deltaTime); }
            case TURN_LEFT -> { v.setPlannedExit(PlannedExit.LEFT); advanceOnSegment(v, deltaTime); }
            case TURN_RIGHT -> { v.setPlannedExit(PlannedExit.RIGHT); advanceOnSegment(v, deltaTime); }
            case U_TURN -> { v.setPlannedExit(PlannedExit.U_TURN); advanceOnSegment(v, deltaTime); }
            default -> advanceOnSegment(v, deltaTime);
        }

        if (v.getSegmentProgress() > 1.0) v.setSegmentProgress(1.0);
        if (v.getSegmentProgress() < 0.0) v.setSegmentProgress(0.0);
    }

    /**
     * Kiểm tra làn {@code targetLaneIdx} có an toàn để {@code v} chuyển vào không.
     *
     * Kiểm tra HAI HƯỚNG, vì bug cũ chỉ kiểm tra xe phía sau:
     *
     *   - Xe phía TRƯỚC trên làn đích: khoảng cách phải >= nửa chiều dài
     *     của cả hai xe + margin. Nếu không kiểm tra điều này, v có thể
     *     "cắt đầu" ngay trước mũi xe khác (gap ~ 0) — và ngay sau khi
     *     đổi làn, xe đó trở thành "xe sau" của v và chạm vào đuôi v.
     *
     *   - Xe phía SAU trên làn đích: phải có đủ khoảng cách phanh
     *     (braking distance) dựa trên tốc độ của xe đó, cộng thêm
     *     nửa chiều dài hai xe — nếu không, v cắt vào ngay trước mũi
     *     xe sau khiến xe sau không kịp phanh.
     *
     * @param extraMargin Khoảng đệm bổ sung (world units) ngoài kích thước xe,
     *                     ví dụ 5.0 cho cảm giác "có khoảng hở" tự nhiên.
     */
    private boolean isLaneSafeToEnter(Vehicle v, RoadSegment seg, int targetLaneIdx, double extraMargin) {
        double maxDecel = HARD_BRAKE;
        double vHalfLen = vehicleLength(v) / 2.0;

        for (Vehicle other : vehicles) {
            if (other == v) continue;
            if (other.getCurrentSegment() != seg) continue;
            if (other.getCurrentLane() == null || other.getCurrentLane().getIndex() != targetLaneIdx) continue;

            double otherHalfLen = vehicleLength(other) / 2.0;
            double dist = v.getPosition().distanceTo(other.getPosition());

            boolean otherIsAhead = (v.getCurrentLane().getDirection() == Lane.Direction.FORWARD)
                    ? other.getSegmentProgress() > v.getSegmentProgress()
                    : other.getSegmentProgress() < v.getSegmentProgress();

            if (otherIsAhead) {
                // Xe phía trước trên làn đích: không được "cắt đầu" sát đuôi xe đó
                double minGap = vHalfLen + otherHalfLen + extraMargin;
                if (dist < minGap) return false;
            } else {
                // Xe phía sau trên làn đích: phải có đủ khoảng cách phanh
                double otherSpeed = other.getSpeed();
                double brakingDist = (otherSpeed * otherSpeed) / (2 * maxDecel)
                        + vHalfLen + otherHalfLen + extraMargin;
                if (dist < brakingDist) return false;
            }
        }
        return true;
    }

    /**
     * Ước lượng chiều dài xe (world units) theo loại xe.
     *
     * Dùng trong isLaneSafeToEnter() để tính khoảng cách an toàn dựa trên
     * kích thước thực — xe to (FireTruck/Ambulance) cần khoảng hở lớn hơn
     * xe máy khi chuyển làn để tránh chạm đuôi nhau.
     */
    private double vehicleLength(Vehicle v) {
        return switch (v.getType()) {
            case BICYCLE   -> 1.8;
            case MOTORBIKE -> 2.2;
            case CAR       -> 4.5;
            case AMBULANCE -> 6.0;
            case FIRETRUCK -> 8.0;
        };
    }

    private void advanceOnSegment(Vehicle v, double deltaTime) {
        RoadSegment seg = v.getCurrentSegment();
        if (seg == null || seg.getLength() < 1.0) return;

        if (v.getLaneChangeProgress() < 1.0) {
            v.setLaneChangeProgress(v.getLaneChangeProgress() + deltaTime / 1.5);
            if (v.getLaneChangeProgress() > 1.0) v.setLaneChangeProgress(1.0);
        }

        double dirMultiplier = (v.getCurrentLane().getDirection() == Lane.Direction.FORWARD) ? 1.0 : -1.0;
        double dp = (v.getSpeed() * deltaTime / seg.getLength()) * dirMultiplier;
        double newProgress = v.getSegmentProgress() + dp;
    
        // Giới hạn progress
        if (newProgress > 1.0) newProgress = 1.0;
        if (newProgress < 0.0) newProgress = 0.0;
    
        // Lấy vị trí dự kiến
        double[] newPose = seg.getPositionOnLane(v.getCurrentLane().getIndex(), newProgress);
        Position newPos = new Position(newPose[0], newPose[1]);
    
        // Kiểm tra va chạm với xe cùng làn, cùng chiều
        for (Vehicle other : vehicles) {
            if (other == v) continue;
            if (other.getCurrentSegment() == seg && other.getCurrentLane().getIndex() == v.getCurrentLane().getIndex()) {
                // Xác định xe phía trước
                boolean isAhead = (dirMultiplier > 0 && other.getSegmentProgress() > v.getSegmentProgress()) ||
                                  (dirMultiplier < 0 && other.getSegmentProgress() < v.getSegmentProgress());
                if (isAhead) {
                    double dist = newPos.distanceTo(other.getPosition());
                    if (dist < 12.0) { // Ngưỡng va chạm (2/3 chiều dài xe)
                        // Không di chuyển, thậm chí phanh thêm
                        v.setSpeed(Math.max(0, v.getSpeed() - HARD_BRAKE * deltaTime));
                        return;
                    }
                }
            }
        } 
        v.setSegmentProgress(newProgress);
        v.syncPositionFromSegment();
    }

    private void executeOnIntersectionPath(Vehicle v, Action action, double deltaTime) {
        IntersectionPath path = v.getActivePath();
        if (path == null) return;

        switch (action) {
            case STOP -> v.setSpeed(Math.max(0, v.getSpeed() - HARD_BRAKE * deltaTime));
            case SLOW_DOWN -> v.setSpeed(Math.max(5.0, v.getSpeed() - NORMAL_BRAKE * deltaTime));
            case ACCELERATE -> {
                if (v.getSpeed() < 10.0) v.setSpeed(10.0);
                v.setSpeed(Math.min(v.getMaxSpeed(), v.getSpeed() + NORMAL_ACCEL * deltaTime));
            }
            case HONK -> v.honk();
            default -> { if (v.getSpeed() < 10.0) v.setSpeed(10.0); }
        }
    
        if (v.getSpeed() > 0) {
            //v.setPathProgress(v.getPathProgress() + v.getSpeed() * deltaTime);
            //pathFollower.syncPose(v, path, v.getPathProgress());
            pathFollower.advance(v, deltaTime);
        }
    }

    private void tryEnterIntersection(Vehicle v) {
        if (v.getTravelMode() != TravelMode.ON_SEGMENT) return;
        if (v.getPlannedExit() == PlannedExit.NONE) return;

        boolean readyToEnter = (v.getCurrentLane().getDirection() == Lane.Direction.FORWARD)
                ? v.getSegmentProgress() >= 1.0
                : v.getSegmentProgress() <= 0.0;

        if (!readyToEnter) return;

        Intersection upcoming = intersectionNavigator.peekUpcomingIntersection(v);
        if (upcoming == null) return;
    
        // Kiểm tra an toàn với các xe đã ở trong giao lộ (canMerge)
        if (!intersectionNavigator.canMerge(v, upcoming, vehicles)) return;

        // ***** BỔ SUNG: Kiểm tra xe khác trên cùng segment cũng sắp vào *****
        for (Vehicle other : vehicles) {
            if (other == v) continue;
            if (other.getTravelMode() == TravelMode.ON_SEGMENT && other.getCurrentSegment() == v.getCurrentSegment()) {
                // Xe khác cùng hướng và đang ở cuối đoạn đường (gần intersection)
                boolean otherReady = (other.getCurrentLane().getDirection() == v.getCurrentLane().getDirection())
                        ? other.getSegmentProgress() >= 0.98
                        : other.getSegmentProgress() <= 0.02;
                if (otherReady) {
                    // Nếu xe khác quá gần (dưới 30m), không cho vào
                    double dist = v.getPosition().distanceTo(other.getPosition());
                    if (dist < 30.0) {
                        return;
                    }
                }
            }
            // Kiểm tra xe đã có planned exit và đang ở gần (tránh hai xe cùng đợi)
            if (other.getPlannedExit() != PlannedExit.NONE && other.getCurrentSegment() == v.getCurrentSegment()) {
                double dist = v.getPosition().distanceTo(other.getPosition());
                if (dist < 40.0) {
                    return; // nhường cho xe đã chờ trước
                }
            }
        }
        // ********************************************************

        IntersectionPath path = intersectionNavigator.buildPath(v);
        if (path == null) {
            v.setPlannedExit(PlannedExit.STRAIGHT);
            path = intersectionNavigator.buildPath(v);
        }
        if (path == null) {
            v.clearPlannedExit();
            return;
        }

        v.enterIntersectionPath(path, path.getIntersection());
        v.clearPlannedExit();
    }

    private void tryExitIntersection(Vehicle v) {
        if (v.getTravelMode() != TravelMode.ON_INTERSECTION_PATH) return;
        if (!v.isIntersectionPathComplete()) return;

        intersectionNavigator.applyExit(v);
        if (v.getSpeed() < 10.0) v.setSpeed(INITIAL_SPEED);
    
        // Sau khi đặt xe vào segment, kiểm tra nếu quá gần xe khác thì dịch lùi
        for (Vehicle other : vehicles) {
            if (other == v) continue;
            if (other.getCurrentSegment() == v.getCurrentSegment() && 
                other.getCurrentLane() == v.getCurrentLane()) {
                double dist = v.getPosition().distanceTo(other.getPosition());
                if (dist < 15.0) {
                    // Dịch lùi lại 10 mét
                    double offset = 10.0 / v.getCurrentSegment().getLength();
                    double newProgress = v.getSegmentProgress() - offset;
                    v.setSegmentProgress(Math.max(0, newProgress));
                    v.syncPositionFromSegment();
                    break;
                }
            }
        }
    }

    // FIX LỖI GIẬT CỤC: Xe chỉ tuân theo đèn giao thông khi đang đứng sát mép giao lộ
    private TrafficLightState getCurrentLightState(Vehicle subject) {
        if (lights.isEmpty() || subject.getCurrentLane() == null) return TrafficLightState.GREEN;

        boolean nearEnd = (subject.getCurrentLane().getDirection() == Lane.Direction.FORWARD)
                ? subject.getSegmentProgress() > 0.85
                : subject.getSegmentProgress() < 0.15;

        if (!nearEnd) return TrafficLightState.GREEN; // Trả về Green nếu đang ở giữa đường

        return lights.get(0).getCurrentState();
    }

    public List<Vehicle> getVehicles() { return Collections.unmodifiableList(vehicles); }
}
