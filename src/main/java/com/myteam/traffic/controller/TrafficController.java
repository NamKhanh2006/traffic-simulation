package com.myteam.traffic.controller;

import com.myteam.traffic.behavior.common.Action;
import com.myteam.traffic.behavior.DriverBehavior;
import com.myteam.traffic.context.RoadContext;
import com.myteam.traffic.light.TrafficLight;
import com.myteam.traffic.light.TrafficLightState;
import com.myteam.traffic.light.SegmentLight;
import com.myteam.traffic.model.geometry.Position;
import com.myteam.traffic.model.geometry.Direction;
import com.myteam.traffic.model.infrastructure.*;
import com.myteam.traffic.model.infrastructure.intersection.Intersection;
import com.myteam.traffic.navigation.IntersectionNavigator;
import com.myteam.traffic.navigation.IntersectionPath;
import com.myteam.traffic.navigation.PathFollower;
import com.myteam.traffic.rule.TrafficRule;
import com.myteam.traffic.ui.ExplosionEffect;
import com.myteam.traffic.ui.SoundManager;
import com.myteam.traffic.vehicle.*;
import com.myteam.traffic.vehicle.emergency.Ambulance;
import com.myteam.traffic.vehicle.emergency.FireTruck;

import java.util.*;
import java.util.function.Supplier;
import java.util.concurrent.CopyOnWriteArrayList;

public class TrafficController {

    // TĂNG LÊN 150.0 ĐỂ QUÉT ĐƯỢC CÁC VÒNG XUYẾN KHỔNG LỒ (Chống lỗi xóa nhầm xe)
    private static final double APPROACHING_THRESHOLD = 200.0;
    private static final double INITIAL_SPEED = 25.0;
    private static final double NORMAL_ACCEL = 8.0;
    private static final double NORMAL_BRAKE = 12.0;
    private static final double HARD_BRAKE = 40.0;

    private final RoadNetwork network;
    private final IntersectionNavigator intersectionNavigator;
    private final PathFollower pathFollower = new PathFollower();
    private final Random random = new Random();

    private final List<Vehicle> vehicles = new CopyOnWriteArrayList<>();
    private final List<TrafficLight> lights = new CopyOnWriteArrayList<>();
    private final List<SegmentLight> segmentLights = new CopyOnWriteArrayList<>();
    private final List<TrafficRule> globalRules = new CopyOnWriteArrayList<>();

    // Danh sách hiệu ứng nổ đang hoạt động
    private final List<ExplosionEffect> explosions = new CopyOnWriteArrayList<>();

    // Khoảng cách an toàn tối thiểu giữa 2 xe (center-to-center) — va chạm thực sự
    private static final double CRASH_DISTANCE = 6.0;

    public TrafficController(RoadNetwork network) {
        if (network == null)
            throw new IllegalArgumentException("Network không được null");
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
            case CAR -> new Car(p, d, behavior);
            case MOTORBIKE -> new Motorbike(p, d, behavior);
            case BICYCLE -> new Bicycle(p, d, behavior);
            case AMBULANCE -> new Ambulance(p, d, behavior);
            case FIRETRUCK -> new FireTruck(p, d, behavior);
        };
    }

    public void addVehicle(Vehicle v) {
        if (v != null)
            vehicles.add(v);
    }

    public void removeVehicle(Vehicle v) {
        if (v != null)
            vehicles.remove(v);
    }

    public void clearVehicles() {
        vehicles.clear();
        explosions.clear();
        SoundManager.stopAll(); // Tắt tất cả âm thanh còi khi xóa sạch xe
    }

    public List<ExplosionEffect> getExplosions() { return Collections.unmodifiableList(explosions); }


    public void addLight(TrafficLight light) {
        if (light != null)
            lights.add(light);
    }

    /** Gắn đèn vào một đoạn đường cụ thể. */
    public void addSegmentLight(SegmentLight sl) {
        if (sl != null) {
            // Xóa đèn cũ trên cùng segment (nếu có) trước khi thêm mới
            segmentLights.removeIf(e -> e.getSegment() == sl.getSegment() && e.isAtEnd() == sl.isAtEnd());
            segmentLights.add(sl);
        }
    }

    /** Xóa đèn khỏi đoạn đường (cả 2 đầu). */
    public void removeSegmentLight(com.myteam.traffic.model.infrastructure.RoadSegment seg) {
        segmentLights.removeIf(sl -> sl.getSegment() == seg);
    }

    /** Xóa đèn khỏi một đầu cụ thể của đoạn đường. */
    public void removeSegmentLight(com.myteam.traffic.model.infrastructure.RoadSegment seg, boolean atEnd) {
        segmentLights.removeIf(sl -> sl.getSegment() == seg && sl.isAtEnd() == atEnd);
    }

    /** Trả về tất cả đèn gắn vào segment cụ thể. */
    public List<SegmentLight> getSegmentLightsForSegment(com.myteam.traffic.model.infrastructure.RoadSegment seg) {
        return segmentLights.stream()
                .filter(sl -> sl.getSegment() == seg)
                .collect(java.util.stream.Collectors.toList());
    }

    public List<SegmentLight> getAllSegmentLights() {
        return Collections.unmodifiableList(segmentLights);
    }

    public void addRule(TrafficRule rule) {
        if (rule != null)
            globalRules.add(rule);
    }

    public List<TrafficRule> getGlobalRules() {
        return Collections.unmodifiableList(globalRules);
    }

    public void tick(double deltaTime) {
        // Cập nhật dying timer và xóa xe đã mờ hết
        vehicles.removeIf(v -> {
            if (v.isDying()) {
                v.setDyingTimer(v.getDyingTimer() + deltaTime);
                return v.getDyingTimer() >= Vehicle.DYING_DURATION;
            }
            return false;
        });

        // Xóa xe ra khỏi đường (tới đầu cuối segment mà không có giao lộ)
        vehicles.removeIf(v -> {
            if (v.isDying()) return false; // đã được xử lý ở trên
            if (v.getTravelMode() != TravelMode.ON_SEGMENT)
                return false;
            if (v.getCurrentLane() == null)
                return true;

            boolean reachedEnd = (v.getCurrentLane().getDirection() == Lane.Direction.FORWARD)
                    ? v.getSegmentProgress() >= 1.0
                    : v.getSegmentProgress() <= 0.0;

            boolean shouldRemove = reachedEnd && findUpcomingIntersection(v, v.getPosition()) == null;
            
            // Tắt còi trước khi xóa xe ưu tiên
            if (shouldRemove && v instanceof com.myteam.traffic.vehicle.emergency.EmergencyVehicle ev) {
                ev.stopSiren();
            }
            
            return shouldRemove;
        });

        // Cập nhật và xóa explosion đã hết
        explosions.removeIf(e -> {
            e.age += deltaTime;
            return e.isDead();
        });

        for (TrafficLight light : lights)
            light.tick(deltaTime);
        for (SegmentLight sl : segmentLights)
            sl.getLight().tick(deltaTime);
        Map<Vehicle, Position> snapshot = takePositionSnapshot();
        Map<Vehicle, Double> pathProgressSnapshot = new HashMap<>();
        Map<Vehicle, Double> speedSnapshot = new HashMap<>();
        for (Vehicle v : vehicles) {
            pathProgressSnapshot.put(v, v.getPathProgress());
            speedSnapshot.put(v, v.getSpeed());
        }

        // Phát hiện va chạm thực sự (overlap nặng) — đánh dấu dying
        detectRealCollisions();

        for (Vehicle v : new ArrayList<>(vehicles)) {
            if (v.isDying()) continue; // bỏ qua xe đang mờ
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

    /**
     * Phát hiện va chạm thực sự khi 2 xe chồng lấp nhau nhiều.
     * Tạo hiệu ứng nổ và đánh dấu để xóa.
     */
    private void detectRealCollisions() {
        List<Vehicle> list = new ArrayList<>(vehicles);
        for (int i = 0; i < list.size(); i++) {
            Vehicle a = list.get(i);
            if (a.isDying()) continue;
            for (int j = i + 1; j < list.size(); j++) {
                Vehicle b = list.get(j);
                if (b.isDying()) continue;

                double dist = a.getPosition().distanceTo(b.getPosition());
                double minDist = CRASH_DISTANCE;

                if (dist < minDist) {
                    // Va chạm! Tạo hiệu ứng nổ tại trung điểm
                    double ex = (a.getX() + b.getX()) / 2.0;
                    double ey = (a.getY() + b.getY()) / 2.0;
                    explosions.add(new ExplosionEffect(ex, ey));

                    // Đánh dấu cả 2 xe dying
                    a.setDying(true);
                    b.setDying(true);
                }
            }
        }
    }


    private void autoSetPlannedExit(Vehicle v) {
        if (v.getTravelMode() != TravelMode.ON_SEGMENT)
            return;
        if (v.getPlannedExit() != PlannedExit.NONE)
            return;

        // Ngưỡng gần cuối đường (giảm xuống 0.7 để đặt hướng sớm hơn)
        boolean nearEnd = (v.getCurrentLane().getDirection() == Lane.Direction.FORWARD)
                ? v.getSegmentProgress() > 0.70
                : v.getSegmentProgress() < 0.30;

        if (!nearEnd)
            return;

        Intersection upcoming = findUpcomingIntersection(v, v.getPosition());
        if (upcoming == null)
            return;

        int roadCount = upcoming.getRoadCount();

        // Nếu giao lộ có từ 5 nhánh trở lên → chọn ngẫu nhiên (bao gồm chéo)
        if (roadCount >= 5) {
            v.setPlannedExit(PlannedExit.RANDOM);
            return;
        }

        // Với giao lộ 3 hoặc 4 nhánh, dùng logic cũ (ưu tiên thẳng, trái, phải)
        Lane currentLane = v.getCurrentLane();
        Set<Lane.Movement> allowed = currentLane.getAllowedMovements();
        List<PlannedExit> possible = new ArrayList<>();

        if (allowed.contains(Lane.Movement.STRAIGHT))
            possible.add(PlannedExit.STRAIGHT);
        if (allowed.contains(Lane.Movement.LEFT))
            possible.add(PlannedExit.LEFT);
        if (allowed.contains(Lane.Movement.RIGHT))
            possible.add(PlannedExit.RIGHT);

        if (possible.isEmpty()) {
            v.setPlannedExit(PlannedExit.STRAIGHT);
            return;
        }

        // Chọn ngẫu nhiên trong các hướng được phép (có thể tỉ lệ)
        PlannedExit exit = possible.get(random.nextInt(possible.size()));
        v.setPlannedExit(exit);
    }

    private Map<Vehicle, Position> takePositionSnapshot() {
        Map<Vehicle, Position> snapshot = new HashMap<>();
        for (Vehicle v : vehicles)
            snapshot.put(v, v.getPosition());
        return Collections.unmodifiableMap(snapshot);
    }

    private RoadContext buildContext(Vehicle subject, Map<Vehicle, Position> snapshot,
            Map<Vehicle, Double> pathProgressSnapshot,
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
        if (current == null)
            current = findCurrentIntersection(subjectPos);

        List<Vehicle> nearby = new ArrayList<>();
        for (Vehicle other : vehicles) {
            if (other == subject)
                continue;
            if (subjectSeg != null && other.getCurrentSegment() == subjectSeg) {
                nearby.add(other);
                continue;
            }
            Position otherPos = snapshot.getOrDefault(other, other.getPosition());
            if (upcoming != null && isAtIntersection(otherPos, upcoming)) {
                nearby.add(other);
                continue;
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
        if (seg == null || lane == null)
            return null;

        double targetX = (lane.getDirection() == Lane.Direction.FORWARD) ? seg.getEndX() : seg.getStartX();
        double targetY = (lane.getDirection() == Lane.Direction.FORWARD) ? seg.getEndY() : seg.getStartY();

        if (pos.distanceTo(new Position(targetX, targetY)) > APPROACHING_THRESHOLD)
            return null;
        return network.findNearestIntersection(targetX, targetY, APPROACHING_THRESHOLD);
    }

    private Intersection findCurrentIntersection(Position pos) {
        for (Intersection inter : network.getIntersections()) {
            if (isAtIntersection(pos, inter))
                return inter;
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
            if (!rule.appliesTo(v))
                continue;
            if (!rule.isAllowed(v, a, ctx))
                return false;
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
        if (seg == null)
            return;

        switch (action) {
            case ACCELERATE -> {
                v.setSpeed(Math.min(v.getMaxSpeed(), v.getSpeed() + NORMAL_ACCEL * deltaTime));
                advanceOnSegment(v, deltaTime);
            }
            case MOVE_FORWARD -> advanceOnSegment(v, deltaTime);
            case SLOW_DOWN -> {
                v.setSpeed(Math.max(0, v.getSpeed() - NORMAL_BRAKE * deltaTime));
                if (v.getSpeed() > 0)
                    advanceOnSegment(v, deltaTime);
            }
            case STOP -> {
                v.setSpeed(Math.max(0, v.getSpeed() - HARD_BRAKE * deltaTime));
                if (v.getSpeed() > 0)
                    advanceOnSegment(v, deltaTime);
            }
            case CHANGE_LANE, OVERTAKE -> {
                v.setSpeed(Math.min(v.getMaxSpeed(), v.getSpeed() + NORMAL_ACCEL * deltaTime));
                processOvertakeLane(v);
                advanceOnSegment(v, deltaTime);
            }
            case YIELD -> {
                if (v.getLaneChangeProgress() < 1.0) {
                    // Đang dạt sang, giữ nguyên/tăng tốc để hoàn thành chuyển làn nhanh chóng
                    v.setSpeed(Math.min(v.getMaxSpeed(), v.getSpeed() + NORMAL_ACCEL * deltaTime));
                } else {
                    boolean yielded = false;
                    int currentIdx = v.getCurrentLane().getIndex();
                    int rightIdx = currentIdx + 1; // Chỉ ưu tiên dạt sang PHẢI
                    if (rightIdx < seg.getLanes().size()
                            && seg.getLanes().get(rightIdx).getDirection() == v.getCurrentLane().getDirection()) {
                        if (isLaneSafeToEnter(v, seg, rightIdx, 5.0)) {
                            v.changeLaneIndex(rightIdx);
                            yielded = true;
                        }
                    }

                    if (!yielded) {
                        // Không thể dạt phải (do vướng xe hoặc hết đường), giảm tốc từ từ để xe ưu tiên
                        // lách sang trái vượt
                        v.setSpeed(Math.max(10.0, v.getSpeed() - NORMAL_BRAKE * deltaTime));
                    } else {
                        v.setSpeed(Math.min(v.getMaxSpeed(), v.getSpeed() + NORMAL_ACCEL * deltaTime));
                    }
                }
                advanceOnSegment(v, deltaTime);
            }
            case HONK -> {
                v.tryHonk();
                advanceOnSegment(v, deltaTime);
            }
            case TURN_LEFT -> {
                v.setPlannedExit(PlannedExit.LEFT);
                advanceOnSegment(v, deltaTime);
            }
            case TURN_RIGHT -> {
                v.setPlannedExit(PlannedExit.RIGHT);
                advanceOnSegment(v, deltaTime);
            }
            case U_TURN -> {
                v.setPlannedExit(PlannedExit.U_TURN);
                advanceOnSegment(v, deltaTime);
            }
            default -> advanceOnSegment(v, deltaTime);
        }

        if (v.getSegmentProgress() > 1.0)
            v.setSegmentProgress(1.0);
        if (v.getSegmentProgress() < 0.0)
            v.setSegmentProgress(0.0);
    }

    /**
     * Kiểm tra làn {@code targetLaneIdx} có an toàn để {@code v} chuyển vào không.
     *
     * Kiểm tra HAI HƯỚNG, vì bug cũ chỉ kiểm tra xe phía sau:
     *
     * - Xe phía TRƯỚC trên làn đích: khoảng cách phải >= nửa chiều dài
     * của cả hai xe + margin. Nếu không kiểm tra điều này, v có thể
     * "cắt đầu" ngay trước mũi xe khác (gap ~ 0) — và ngay sau khi
     * đổi làn, xe đó trở thành "xe sau" của v và chạm vào đuôi v.
     *
     * - Xe phía SAU trên làn đích: phải có đủ khoảng cách phanh
     * (braking distance) dựa trên tốc độ của xe đó, cộng thêm
     * nửa chiều dài hai xe — nếu không, v cắt vào ngay trước mũi
     * xe sau khiến xe sau không kịp phanh.
     *
     * @param extraMargin Khoảng đệm bổ sung (world units) ngoài kích thước xe,
     *                    ví dụ 5.0 cho cảm giác "có khoảng hở" tự nhiên.
     */
    private boolean isLaneSafeToEnter(Vehicle v, RoadSegment seg, int targetLaneIdx, double safeRadius) {
        // Lấy vị trí dự kiến trên làn mới (tại vị trí hiện tại hoặc gần đó)
        double currentT = v.getSegmentProgress();
        double[] targetPose = seg.getPositionOnLane(targetLaneIdx, currentT);
        double targetX = targetPose[0];
        double targetY = targetPose[1];

        // Dùng AABB để kiểm tra va chạm
        if (wouldCollide(v, targetX, targetY, seg, targetLaneIdx)) {
            return false;
        }

        // Kiểm tra khoảng cách phòng ngừa thêm
        for (Vehicle other : vehicles) {
            if (other == v)
                continue;
            if (other.getCurrentSegment() != seg)
                continue;
            if (other.getCurrentLane().getIndex() != targetLaneIdx)
                continue;
            double dist = v.getPosition().distanceTo(other.getPosition());
            if (dist < safeRadius) {
                return false;
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
            case BICYCLE -> 1.8;
            case MOTORBIKE -> 2.2;
            case CAR -> 4.5;
            case AMBULANCE -> 6.0;
            case FIRETRUCK -> 8.0;
        };
    }

    private void advanceOnSegment(Vehicle v, double deltaTime) {
        RoadSegment seg = v.getCurrentSegment();
        if (seg == null || seg.getLength() < 1.0)
            return;

        if (v.getLaneChangeProgress() < 1.0) {
            v.setLaneChangeProgress(Math.min(1.0, v.getLaneChangeProgress() + deltaTime / 1.5));
        }

        double dirMultiplier = (v.getCurrentLane().getDirection() == Lane.Direction.FORWARD) ? 1.0 : -1.0;
        double dp = (v.getSpeed() * deltaTime / seg.getLength()) * dirMultiplier;
        double newProgress = v.getSegmentProgress() + dp;
        newProgress = Math.max(0, Math.min(1, newProgress));

        // FIX LỖI VƯỢT STOPLINE: Dừng trước stopline (mép ngoài giao lộ)
        TrafficLightState lightState = getCurrentLightState(v);
        if (lightState == TrafficLightState.RED || lightState == TrafficLightState.YELLOW) {
            boolean isForward = (v.getCurrentLane().getDirection() == Lane.Direction.FORWARD);
            // stopMargin = phần progress tương ứng với (bán kính giao lộ + khoảng đệm 5 units)
            // Xe sẽ dừng ngay sát mép ngoài vòng tròn giao lộ
            com.myteam.traffic.model.infrastructure.intersection.Intersection upcoming = intersectionNavigator.peekUpcomingIntersection(v);
            double interRadius = (upcoming != null) ? upcoming.getRenderData().radius : 30.0;
            double stopMargin = (interRadius + 5.0) / seg.getLength();

            if (isForward) {
                double stopProgress = 1.0 - stopMargin;
                // Chỉ clamp nếu xe CHƯA vượt qua stopline
                if (v.getSegmentProgress() < stopProgress + 0.005) {
                    newProgress = Math.min(newProgress, stopProgress);
                }
            } else {
                double stopProgress = stopMargin;
                if (v.getSegmentProgress() > stopProgress - 0.005) {
                    newProgress = Math.max(newProgress, stopProgress);
                }
            }
        }

        // Lấy vị trí dự kiến (bao gồm cả offset khi đang chuyển làn)
        double[] newPose;
        if (v.getLaneChangeProgress() < 1.0 && v.getPreviousLane() != null) {
            // Nếu đang chuyển làn, tính vị trí nội suy giữa làn cũ và làn mới
            double[] fromPose = seg.getPositionOnLane(v.getPreviousLane().getIndex(), newProgress);
            double[] toPose = seg.getPositionOnLane(v.getCurrentLane().getIndex(), newProgress);
            double t = v.getLaneChangeProgress();
            double x = fromPose[0] + (toPose[0] - fromPose[0]) * t;
            double y = fromPose[1] + (toPose[1] - fromPose[1]) * t;
            newPose = new double[] { x, y, toPose[2] };
        } else {
            newPose = seg.getPositionOnLane(v.getCurrentLane().getIndex(), newProgress);
        }

        double newX = newPose[0];
        double newY = newPose[1];

        // Kiểm tra va chạm với tất cả xe
        if (wouldCollide(v, newX, newY, seg, v.getCurrentLane().getIndex())) {
            // Nếu va chạm, phanh gấp và không di chuyển
            v.setSpeed(Math.max(0, v.getSpeed() - HARD_BRAKE * deltaTime));
            return;
        }

        v.setSegmentProgress(newProgress);
        v.syncPositionFromSegment();
    }

    private void executeOnIntersectionPath(Vehicle v, Action action, double deltaTime) {
        IntersectionPath path = v.getActivePath();
        if (path == null)
            return;

        // Kiểm tra khoảng cách với xe phía trước trong cùng giao lộ
        double frontGap = getIntersectionFrontGap(v);

        // Tốc độ mà xe được phép theo
        double targetSpeed;
        if (frontGap < 10.0) {
            // Quá gần xe phía trước — giảm tốc nhưng KHÔNG dừng hẳn
            targetSpeed = Math.max(8.0, frontGap / 10.0 * v.getMaxSpeed() * 0.5);
        } else {
            switch (action) {
                case STOP, SLOW_DOWN -> targetSpeed = Math.max(10.0, v.getSpeed() - NORMAL_BRAKE * deltaTime);
                case ACCELERATE -> targetSpeed = Math.min(v.getMaxSpeed(), v.getSpeed() + NORMAL_ACCEL * deltaTime);
                default -> targetSpeed = Math.max(10.0, v.getSpeed()); // giữ tốc, ít nhất 10
            }
        }

        if (action == Action.HONK) v.tryHonk();
        v.setSpeed(targetSpeed);

        if (v.getSpeed() > 0) {
            pathFollower.advance(v, deltaTime);
        }
    }

    /**
     * Tính khoảng cách tới xe phía trước trong cùng giao lộ (theo pathProgress).
     * Trả về Double.MAX_VALUE nếu không có ai phía trước.
     */
    private double getIntersectionFrontGap(Vehicle v) {
        Intersection inter = v.getCurrentIntersection();
        double myProgress = v.getPathProgress();
        double minGap = Double.MAX_VALUE;

        for (Vehicle other : vehicles) {
            if (other == v) continue;
            if (other.isDying()) continue;
            if (other.getCurrentIntersection() != inter) continue;

            double otherProgress = other.getPathProgress();
            double gap = otherProgress - myProgress; // dương = ở phía trước

            if (gap > 0 && gap < minGap) {
                // Dùng khoảng cách thực tế để đảm bảo
                double dist = v.getPosition().distanceTo(other.getPosition());
                minGap = dist;
            }
        }
        return minGap;
    }

    private void tryEnterIntersection(Vehicle v) {
        if (v.getTravelMode() != TravelMode.ON_SEGMENT)
            return;
        if (v.getPlannedExit() == PlannedExit.NONE)
            return;

        boolean readyToEnter = (v.getCurrentLane().getDirection() == Lane.Direction.FORWARD)
                ? v.getSegmentProgress() >= 1.0
                : v.getSegmentProgress() <= 0.0;

        if (!readyToEnter)
            return;

        Intersection upcoming = intersectionNavigator.peekUpcomingIntersection(v);
        if (upcoming == null)
            return;

        // Kiểm tra an toàn với các xe đã ở trong giao lộ (canMerge)
        if (!intersectionNavigator.canMerge(v, upcoming, vehicles))
            return;

        // ***** BỔ SUNG: Kiểm tra xe khác trên cùng segment cũng sắp vào *****
        for (Vehicle other : vehicles) {
            if (other == v)
                continue;
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
        if (v.getTravelMode() != TravelMode.ON_INTERSECTION_PATH)
            return;
        if (!v.isIntersectionPathComplete())
            return;

        intersectionNavigator.applyExit(v);
        if (v.getSpeed() < 10.0)
            v.setSpeed(INITIAL_SPEED);

        // Sau khi đặt xe vào segment, kiểm tra nếu quá gần xe khác thì dịch lùi
        for (Vehicle other : vehicles) {
            if (other == v)
                continue;
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

    // Xe nhận diện đèn dựa trên khoảng cách đến mép ngoài giao lộ (radius), không phải tâm
    private TrafficLightState getCurrentLightState(Vehicle subject) {
        Lane lane = subject.getCurrentLane();
        RoadSegment seg = subject.getCurrentSegment();
        if (lane == null || seg == null)
            return TrafficLightState.GREEN;

        boolean isForward = (lane.getDirection() == Lane.Direction.FORWARD);
        double targetX = isForward ? seg.getEndX() : seg.getStartX();
        double targetY = isForward ? seg.getEndY() : seg.getStartY();
        // distToCenter = khoảng cách đến TÂM giao lộ (= điểm cuối segment)
        double distToCenter = subject.getPosition().distanceTo(new Position(targetX, targetY));

        // Lấy bán kính giao lộ để tính khoảng cách đến mép ngoài (stopline)
        com.myteam.traffic.model.infrastructure.intersection.Intersection upcoming = intersectionNavigator.peekUpcomingIntersection(subject);
        double interRadius = (upcoming != null) ? upcoming.getRenderData().radius : 30.0;

        // distToStopLine = khoảng cách từ xe đến vạch dừng thực tế (được tính bằng interRadius + 5.0)
        // Nếu âm → xe đã vượt qua vạch dừng rồi → phải tiếp tục đi, không áp dụng đèn nữa
        double distToStopLine = distToCenter - (interRadius + 5.0);

        // Chỉ áp dụng đèn khi xe đang tiến đến stopline (trong ngưỡng 60 units)
        // Nếu khoảng cách âm (< -0.1), tức là xe đã chớm qua vạch dừng -> trả về GREEN để đi luôn
        if (distToStopLine > 60.0 || distToStopLine < -0.1)
            return TrafficLightState.GREEN;

        // Xe đi FORWARD → đèn ở End; Xe đi BACKWARD → đèn ở Start
        boolean atEnd = isForward;
        for (SegmentLight sl : segmentLights) {
            if (sl.getSegment() == seg && sl.isAtEnd() == atEnd) {
                return sl.getLight().getCurrentState();
            }
        }

        // Fallback: đèn global
        if (!lights.isEmpty())
            return lights.get(0).getCurrentState();

        return TrafficLightState.GREEN;
    }

    public List<Vehicle> getVehicles() {
        return Collections.unmodifiableList(vehicles);
    }

    private void processOvertakeLane(Vehicle v) {
        if (v.getTravelMode() != TravelMode.ON_SEGMENT)
            return;
        if (v.getLaneChangeProgress() < 1.0)
            return;
        if (v.getSpeed() < 10.0)
            return;

        RoadSegment seg = v.getCurrentSegment();
        if (seg == null)
            return;

        Intersection upcoming = intersectionNavigator.peekUpcomingIntersection(v);
        if (upcoming != null
                && v.getPosition().distanceTo(new Position(upcoming.getCenterX(), upcoming.getCenterY())) < 50.0) {
            TrafficLightState light = getCurrentLightState(v);
            if (light == TrafficLightState.RED || light == TrafficLightState.YELLOW) {
                return;
            }
        }

        boolean hasSlowCarAhead = false;
        for (Vehicle other : vehicles) {
            if (other == v)
                continue;
            if (other.getCurrentSegment() == seg && other.getCurrentLane() == v.getCurrentLane()) {
                double dist = v.getPosition().distanceTo(other.getPosition());
                boolean isBehind = (v.getCurrentLane().getDirection() == Lane.Direction.FORWARD)
                        ? v.getSegmentProgress() < other.getSegmentProgress()
                        : v.getSegmentProgress() > other.getSegmentProgress();
                if (isBehind && dist < 40.0 && other.getSpeed() < v.getSpeed() - 2.0) {
                    hasSlowCarAhead = true;
                    break;
                }
            }
        }

        if (!hasSlowCarAhead)
            return;

        int currentIdx = v.getCurrentLane().getIndex();
        int newIdx = -1;
        if (currentIdx - 1 >= 0
                && seg.getLanes().get(currentIdx - 1).getDirection() == v.getCurrentLane().getDirection()) {
            newIdx = currentIdx - 1;
        } else if (currentIdx + 1 < seg.getLanes().size()
                && seg.getLanes().get(currentIdx + 1).getDirection() == v.getCurrentLane().getDirection()) {
            newIdx = currentIdx + 1;
        }

        if (newIdx != -1 && isLaneSafeToEnter(v, seg, newIdx, 15.0)) {
            v.changeLaneIndex(newIdx);
        }
    }

    /**
     * Kiểm tra va chạm bằng khoảng cách center-to-center với ngưỡng dựa trên kích thước xe.
     *
     * Thay thế AABB cũ vốn bỏ qua góc quay của xe.
     * Thếm xử lý xe đang trong giao lộ (segment = null).
     */
    private boolean wouldCollide(Vehicle v, double newX, double newY, RoadSegment newSeg, int newLaneIdx) {
        // Nửa chiều dài xe (truc X) — dùng chiều dài vì có thể quay ngang
        double vHalfLen = v.getWidth() / 2.0;
        Position newPos = new Position(newX, newY);

        for (Vehicle other : vehicles) {
            if (other == v) continue;
            if (other.isDying()) continue;

            // Chỉ kiểm tra xe cùng segment hoặc cùng giao lộ
            boolean sameSeg = (newSeg != null) && (other.getCurrentSegment() == newSeg);
            boolean sameInter = (newSeg == null) && (v.getCurrentIntersection() != null)
                    && (v.getCurrentIntersection() == other.getCurrentIntersection());

            if (!sameSeg && !sameInter) continue;

            // Kiểm tra xe trên cùng segment nhưng khác hướng thì bỏ qua
            if (sameSeg && other.getCurrentLane() != null && v.getCurrentLane() != null) {
                if (other.getCurrentLane().getDirection() != v.getCurrentLane().getDirection()) continue;
                // Chỉ kiểm tra xe cùng làn
                if (other.getCurrentLane().getIndex() != newLaneIdx) continue;

                // FIX DEADLOCK: Bỏ qua xe ở PHÍA SAU!
                // Nếu không bỏ qua, xe đi trước sẽ phanh lại vì xe đi sau quá gần -> kẹt cứng cả hai!
                double myProg = v.getSegmentProgress();
                double otherProg = other.getSegmentProgress();
                if (v.getCurrentLane().getDirection() == Lane.Direction.FORWARD) {
                    if (otherProg < myProg) continue; // xe kia ở sau
                } else {
                    if (otherProg > myProg) continue; // xe kia ở sau (do BACKWARD đi từ 1->0)
                }
            }

            double otherHalfLen = other.getWidth() / 2.0;
            double safeGap = vHalfLen + otherHalfLen + 2.0; // +2 buffer

            double dist = newPos.distanceTo(other.getPosition());
            if (dist < safeGap) {
                return true;
            }
        }
        return false;
    }
}
