package com.myteam.traffic.controller;

import com.myteam.traffic.behavior.common.Action;
import com.myteam.traffic.behavior.common.Turn;
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

/**
 * Bộ điều phối trung tâm của hệ thống mô phỏng giao thông.
 * <p>
 * Mỗi tick, controller thực hiện:
 * <ol>
 *   <li>Cập nhật tất cả đèn giao thông</li>
 *   <li>Chụp ảnh vị trí tất cả xe (snapshot)</li>
 *   <li>Với mỗi xe: tạo RoadContext → behavior quyết định Action → kiểm tra luật → thực thi (hoặc fallback)</li>
 *   <li>Xử lý vào/ra giao lộ</li>
 * </ol>
 * <p>
 * Kiến trúc tuân thủ nguyên tắc:
 * <ul>
 *   <li><b>Vehicle</b> chỉ cung cấp atomic methods (thay đổi trạng thái vật lý)</li>
 *   <li><b>DriverBehavior</b> chịu trách nhiệm ra quyết định (Action)</li>
 *   <li><b>Utility classes</b> (DistanceKeeping, LaneChange, OvertakeStrategy) đảm nhiệm các kỹ năng phức tạp</li>
 *   <li><b>TrafficController</b> chỉ điều phối và kiểm tra luật</li>
 * </ul>
 */
public class TrafficController {

    // ── Ngưỡng khoảng cách để xác định xe "sắp tới" intersection ──
    private static final double APPROACHING_THRESHOLD = 80.0;

    // ── Hạ tầng ───────────────────────────────────────────────────
    private final RoadNetwork network;
    private final IntersectionNavigator intersectionNavigator;
    private final PathFollower pathFollower = new PathFollower();

    // ── Danh sách thực thể trong simulation ──────────────────────
    private final List<Vehicle> vehicles = new ArrayList<>();
    private final List<TrafficLight> lights = new ArrayList<>();

    // ── Luật giao thông (tái sử dụng từ teammate) ─────────────────
    private final List<TrafficRule> globalRules = new ArrayList<>();

    // ── Factory để tạo xe (cho phép mở rộng) ──────────────────────
    private Supplier<Vehicle> vehicleFactory = () -> new Car(new Position(0,0), new com.myteam.traffic.model.geometry.Direction(0), null);

    // ═══════════════════════════════════════════════════════════════
    // Constructor
    // ═══════════════════════════════════════════════════════════════

    public TrafficController(RoadNetwork network) {
        if (network == null) throw new IllegalArgumentException("Network không được null");
        this.network = network;
        this.intersectionNavigator = new IntersectionNavigator(network);
        // Mặc định tạo xe Car, có thể thay bằng setVehicleFactory
        this.vehicleFactory = () -> new Car(new Position(0, 0), new Direction(0), null);
    }

    // ═══════════════════════════════════════════════════════════════
    // API công khai – thêm/xoá thực thể và luật
    // ═══════════════════════════════════════════════════════════════

    /**
     * Đặt factory để tạo xe khi spawn.
     * @param factory Supplier trả về đối tượng Vehicle đã được cấu hình (vị trí, hướng, behavior sẽ được ghi đè sau)
     */
    public void setVehicleFactory(Supplier<Vehicle> factory) {
        this.vehicleFactory = factory;
    }

    /**
     * Sinh một xe mới tại đầu vào của một đoạn đường.
     * @param entrySegment Đoạn đường bắt đầu
     * @param lane Làn đường
     * @param behavior Hành vi lái xe
     * @param vehicleType Loại xe (để tạo đúng lớp con)
     */
    public void spawnVehicle(RoadSegment entrySegment, Lane lane, DriverBehavior behavior, VehicleType vehicleType) {
        Vehicle v = createVehicleByType(vehicleType, behavior);
        v.placeOnSegment(entrySegment, lane, 0.0);
        addVehicle(v);
    }

    private Vehicle createVehicleByType(VehicleType type, DriverBehavior behavior) {
        Position startPos = new Position(0, 0);
        Direction startDir = new Direction(0);
        com.myteam.traffic.model.geometry.Direction dir = new com.myteam.traffic.model.geometry.Direction(0);
        switch (type) {
            case CAR: return new Car(startPos, startDir, behavior);
            case MOTORBIKE: return new Motorbike(startPos, startDir, behavior);
            case BICYCLE: return new Bicycle(startPos, startDir, behavior);
            case AMBULANCE: return new Ambulance(startPos, startDir, behavior);
            case FIRETRUCK: return new FireTruck(startPos, startDir, behavior);
            default: return new Car(startPos, startDir, behavior);
        }
    }

    public void addVehicle(Vehicle v) {
        if (v != null) vehicles.add(v);
    }

    public void addLight(TrafficLight light) {
        if (light != null) lights.add(light);
    }

    public void addRule(TrafficRule rule) {
        if (rule != null) globalRules.add(rule);
    }

    public void removeRule(TrafficRule rule) {
        globalRules.remove(rule);
    }

    public List<TrafficRule> getGlobalRules() {
        return Collections.unmodifiableList(globalRules);
    }

    // ═══════════════════════════════════════════════════════════════
    // TICK – vòng lặp chính
    // ═══════════════════════════════════════════════════════════════

    public void tick(double deltaTime) {
        // 1. Loại bỏ xe đã hoàn thành hành trình (không còn đường phía trước)
        vehicles.removeIf(v ->
                v.getTravelMode() == TravelMode.ON_SEGMENT &&
                        v.getSegmentProgress() >= 1.0 &&
                        findUpcomingIntersection(v, v.getPosition()) == null
        );

        // 2. Cập nhật tất cả đèn giao thông trước khi xe ra quyết định
        for (TrafficLight light : lights) {
            light.tick();
        }

        // 3. Chụp ảnh vị trí tất cả xe TẠI ĐẦU TICK (quan trọng để đảm bảo công bằng)
        Map<Vehicle, Position> snapshot = takePositionSnapshot();

        // 4. Xử lý từng xe
        for (Vehicle v : vehicles) {
            RoadContext ctx = buildContext(v, snapshot);
            processVehicle(v, ctx, deltaTime);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Snapshot vị trí
    // ═══════════════════════════════════════════════════════════════

    private Map<Vehicle, Position> takePositionSnapshot() {
        Map<Vehicle, Position> snapshot = new HashMap<>();
        for (Vehicle v : vehicles) {
            snapshot.put(v, v.getPosition());
        }
        return Collections.unmodifiableMap(snapshot);
    }

    // ═══════════════════════════════════════════════════════════════
    // Xây dựng RoadContext cho một xe
    // ═══════════════════════════════════════════════════════════════

    private RoadContext buildContext(Vehicle subject, Map<Vehicle, Position> snapshot) {
        TrafficLightState lightState = getCurrentLightState(subject);
        List<Vehicle> nearby = findNearbyVehicles(subject, snapshot);

        return new RoadContext.Builder()
                .subject(subject)
                .lightState(lightState)
                .currentLane(subject.getCurrentLane())
                .currentSegment(subject.getCurrentSegment())
                .nearbyVehicles(nearby)
                .positionSnapshot(snapshot)
                .build();
    }

    // ═══════════════════════════════════════════════════════════════
    // Tìm xe lân cận theo topology (3 case)
    // ═══════════════════════════════════════════════════════════════

    private List<Vehicle> findNearbyVehicles(Vehicle subject, Map<Vehicle, Position> snapshot) {
        Position subjectPos = snapshot.getOrDefault(subject, subject.getPosition());
        RoadSegment subjectSegment = subject.getCurrentSegment();
        Intersection upcoming = findUpcomingIntersection(subject, subjectPos);
        Intersection current = subject.getCurrentIntersection();
        if (current == null) {
            current = findCurrentIntersection(subjectPos);
        }

        List<Vehicle> nearby = new ArrayList<>();
        for (Vehicle other : vehicles) {
            if (other == subject) continue;

            // Case 1: cùng RoadSegment
            if (subjectSegment != null && other.getCurrentSegment() == subjectSegment) {
                nearby.add(other);
                continue;
            }

            Position otherPos = snapshot.getOrDefault(other, other.getPosition());

            // Case 2: other đang ở intersection mà subject sắp tới
            if (upcoming != null && isAtIntersection(otherPos, upcoming)) {
                nearby.add(other);
                continue;
            }

            // Case 3: cả hai cùng ở một intersection
            if (current != null && isAtIntersection(otherPos, current)) {
                nearby.add(other);
            }
        }
        return nearby;
    }

    private Intersection findUpcomingIntersection(Vehicle subject, Position subjectPos) {
        RoadSegment seg = subject.getCurrentSegment();
        Lane lane = subject.getCurrentLane();
        if (seg == null || lane == null) return null;

        double targetX, targetY;
        if (lane.getDirection() == Lane.Direction.FORWARD) {
            targetX = seg.getEndX();
            targetY = seg.getEndY();
        } else {
            targetX = seg.getStartX();
            targetY = seg.getStartY();
        }

        double distToEnd = subjectPos.distanceTo(new Position(targetX, targetY));
        if (distToEnd > APPROACHING_THRESHOLD) return null;

        return network.findNearestIntersection(targetX, targetY, 60.0);
    }

    private Intersection findCurrentIntersection(Position subjectPos) {
        for (Intersection inter : network.getIntersections()) {
            if (isAtIntersection(subjectPos, inter)) return inter;
        }
        return null;
    }

    private boolean isAtIntersection(Position pos, Intersection intersection) {
        double radius = intersection.getRenderData().radius;
        double cx = intersection.getCenterX();
        double cy = intersection.getCenterY();
        return pos.distanceTo(new Position(cx, cy)) <= radius;
    }

    // ═══════════════════════════════════════════════════════════════
    // Xử lý một xe: quyết định → kiểm tra → thực thi (hoặc fallback)
    // ═══════════════════════════════════════════════════════════════

    private void processVehicle(Vehicle v, RoadContext ctx, double deltaTime) {
        Action proposed = v.getBehavior().decideAction(v, ctx);
        if (isVehicleAllowed(v, proposed, ctx)) {
            executeAction(v, proposed, deltaTime);
        } else {
            Action fallback = v.getBehavior().handleRejection(v, ctx, proposed);
            executeAction(v, fallback, deltaTime);
        }
        // Xử lý vào/ra giao lộ sau khi di chuyển
        tryEnterIntersection(v);
        tryExitIntersection(v);
    }

    /**
     * Kiểm tra action có được phép dựa trên tất cả luật (global + local).
     * Xe khẩn cấp được miễn trừ nếu rule.appliesTo() trả false.
     */
    public boolean isVehicleAllowed(Vehicle v, Action a, RoadContext ctx) {
        List<TrafficRule> allRules = new ArrayList<>(globalRules);
        allRules.addAll(ctx.getLocalRules());
        allRules.sort(Comparator.comparingInt(TrafficRule::getPriority).reversed());

        for (TrafficRule rule : allRules) {
            if (!rule.appliesTo(v)) continue;
            if (!rule.isAllowed(v, a, ctx)) {
                System.out.printf("[CONTROLLER] REJECTED: %s → %s (rule: %s)%n",
                        v.getType(), a, rule.getClass().getSimpleName());
                return false;
            }
        }
        return true;
    }

    // ═══════════════════════════════════════════════════════════════
    // Thực thi action (tách biệt theo TravelMode)
    // ═══════════════════════════════════════════════════════════════

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
        double length = seg.getLength();
        switch (action) {
            case MOVE_FORWARD:
                v.setSegmentProgress(v.getSegmentProgress() + v.getSpeed() * deltaTime / v.getCurrentSegment().getLength());
                v.syncPositionFromSegment();
                break;
            case ACCELERATE:
                v.setSpeed(Math.min(v.getMaxSpeed(), v.getSpeed() + 5.0 * deltaTime));
                break;
            case SLOW_DOWN:
                v.setSpeed(Math.max(0, v.getSpeed() - 8.0 * deltaTime));
                break;
            case STOP:
                v.setSpeed(0);
                break;
            case CHANGE_LANE:
                // Đơn giản: chuyển sang làn kề bên phải nếu có
                Lane currentLane = v.getCurrentLane();
                int currentIdx = currentLane.getIndex();
                int targetIdx = currentIdx + 1; // ưu tiên sang phải
                if (targetIdx < seg.getLanes().size()) {
                    // Cần có RoadContext để kiểm tra MOBIL – nhưng ở đây không có
                    // Giải pháp: tạo context tạm thời với snapshot hiện tại
                    // Tuy nhiên, để đơn giản, có thể bỏ qua kiểm tra an toàn khi chạy demo
                    v.changeLaneIndex(targetIdx);
                }
                break;
            case OVERTAKE:
                // Vượt: tăng tốc + chuyển làn trái
                v.setSpeed(Math.min(v.getMaxSpeed(), v.getSpeed() + 5.0 * deltaTime));
                int leftIdx = v.getCurrentLane().getIndex() - 1;
                if (leftIdx >= 0) {
                    v.changeLaneIndex(leftIdx);
                }
                break;
            case TURN_LEFT:
                /*
                v.setPlannedExit(PlannedExit.LEFT);
                break;
                */
               if (Turn.executeTurn(v, PlannedExit.LEFT)) {
                    // thành công, không cần làm gì thêm
                } else {
                // không được phép rẽ, có thể fallback về MOVE_FORWARD
                    v.setPlannedExit(PlannedExit.NONE);
                }   
                break;
            case TURN_RIGHT:
                /*
                v.setPlannedExit(PlannedExit.RIGHT);
                break;
                */
                if (Turn.executeTurn(v, PlannedExit.RIGHT)) {
                    // thành công, không cần làm gì thêm
                } else {
                    // không được phép rẽ, có thể fallback về MOVE_FORWARD
                    v.setPlannedExit(PlannedExit.NONE);
                }
                break;
            case U_TURN:
                /*v.setPlannedExit(PlannedExit.LEFT);
                break;*/
                if (Turn.executeTurn(v, PlannedExit.LEFT)) {
                    // thành công, không cần làm gì thêm
                } else {
                    // không được phép rẽ, có thể fallback về MOVE_FORWARD
                    v.setPlannedExit(PlannedExit.NONE);
                }
                break;
            case HONK:
                v.honk();
                break;
            default:
                v.setSegmentProgress((v.getSegmentProgress() + v.getSpeed() * deltaTime) / v.getCurrentSegment().getLength());
                v.syncPositionFromSegment();
        }
        // Đảm bảo tiến độ không vượt quá 1
        if (v.getSegmentProgress() > 1.0) v.setSegmentProgress(1.0);
    }

     private void executeOnIntersectionPath(Vehicle v, Action action, double deltaTime) {
        IntersectionPath path = v.getActivePath();
        if (path == null) return;
        switch (action) {
            case MOVE_FORWARD:
                v.setPathProgress(v.getPathProgress() + v.getSpeed() * deltaTime);
                pathFollower.syncPose(v, path, v.getPathProgress());
                break;
            case ACCELERATE:
                v.setSpeed(Math.min(v.getMaxSpeed(), v.getSpeed() + 5.0 * deltaTime));
                v.setPathProgress(v.getPathProgress() + v.getSpeed() * deltaTime);
                pathFollower.syncPose(v, path, v.getPathProgress());
                break;
            case SLOW_DOWN:
                v.setSpeed(Math.max(0, v.getSpeed() - 8.0 * deltaTime));
                v.setPathProgress(v.getPathProgress() + v.getSpeed() * deltaTime);
                pathFollower.syncPose(v, path, v.getPathProgress());
                break;
            case STOP:
                v.setSpeed(0);
                break;
            case HONK:
                v.honk();
                break;
            default:
                v.setPathProgress(v.getPathProgress() + v.getSpeed() * deltaTime);
                pathFollower.syncPose(v, path, v.getPathProgress());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Quản lý vào/ra giao lộ (navigation)
    // ═══════════════════════════════════════════════════════════════

    private void tryEnterIntersection(Vehicle v) {
        if (v.getTravelMode() != TravelMode.ON_SEGMENT) return;
        if (v.getPlannedExit() == PlannedExit.NONE) return;
        if (v.getSegmentProgress() < 1.0) return;

        Intersection upcoming = intersectionNavigator.peekUpcomingIntersection(v);
        if (upcoming != null && !intersectionNavigator.canMerge(v, upcoming, vehicles)) {
            return; // có xe trên cung quá gần
        }

        IntersectionPath path = intersectionNavigator.buildPath(v);
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
    }

    // ═══════════════════════════════════════════════════════════════
    // Helper: lấy trạng thái đèn giao thông (hiện tại lấy đèn đầu tiên)
    // TODO: cải tiến để lấy đèn đúng với intersection sắp tới của xe
    // ═══════════════════════════════════════════════════════════════

    private TrafficLightState getCurrentLightState(Vehicle subject) {
        if (lights.isEmpty()) return TrafficLightState.GREEN;
        // Tìm đèn giao thông gắn với intersection sắp tới (nếu có)
        Intersection upcoming = findUpcomingIntersection(subject, subject.getPosition());
        if (upcoming != null) {
            // Giả sử mỗi intersection có thể có đèn riêng – ở đây đơn giản trả về đèn đầu tiên
            // Trong hệ thống hoàn chỉnh, bạn sẽ map intersection -> TrafficLight
        }
        return lights.get(0).getCurrentState();
    }
}
