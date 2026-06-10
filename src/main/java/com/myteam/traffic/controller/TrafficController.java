package com.myteam.traffic.controller;

import com.myteam.traffic.behavior.common.Action;
import com.myteam.traffic.behavior.DriverBehavior;
import com.myteam.traffic.context.RoadContext;
import com.myteam.traffic.light.TrafficLight;
import com.myteam.traffic.light.TrafficLightState;
import com.myteam.traffic.model.geometry.Position;
import com.myteam.traffic.model.infrastructure.Lane;
import com.myteam.traffic.model.infrastructure.RoadNetwork;
import com.myteam.traffic.model.infrastructure.RoadSegment;
import com.myteam.traffic.model.infrastructure.intersection.Intersection;
import com.myteam.traffic.navigation.IntersectionNavigator;
import com.myteam.traffic.navigation.IntersectionPath;
import com.myteam.traffic.navigation.PathFollower;
import com.myteam.traffic.rule.TrafficRule;
import com.myteam.traffic.vehicle.PlannedExit;
import com.myteam.traffic.vehicle.TravelMode;
import com.myteam.traffic.vehicle.Vehicle;

import java.util.*;

/**
 * Bộ điều phối trung tâm của hệ thống mô phỏng giao thông.
 *
 * Mỗi tick, controller thực hiện 3 việc theo thứ tự:
 *
 *   1. Tick tất cả đèn giao thông
 *   2. Chụp ảnh vị trí tất cả xe (positionSnapshot)
 *   3. Với mỗi xe:
 *        a. Xây dựng RoadContext (bao gồm tìm nearbyVehicles)
 *        b. Behavior đề xuất action
 *        c. isVehicleAllowed() kiểm tra action qua các rule
 *        d. Nếu bị từ chối → handleRejection() → thử lại
 *        e. Thực thi action
 *
 * ┌─────────────────────────────────────────────────────────────┐
 * │ isVehicleAllowed() — tái sử dụng từ code của teammate       │
 * │                                                             │
 * │  globalRules + ctx.getLocalRules()                          │
 * │       → sort theo priority (desc)                           │
 * │       → bỏ qua rule nếu appliesTo(v) == false               │
 * │       → rule đầu tiên trả false → REJECT ngay lập tức       │
 * └─────────────────────────────────────────────────────────────┘
 *
 * nearbyVehicles — định nghĩa theo topology mạng lưới đường:
 *   Case 1: Cùng RoadSegment với subject
 *   Case 2: Đang ở Intersection mà subject sắp đi tới
 *   Case 3: Đang ở cùng Intersection với subject
 */
public class TrafficController {

    // ── Ngưỡng khoảng cách để xác định xe "sắp tới" intersection ──
    // Nếu xe còn cách điểm cuối đoạn đường dưới 80 world units
    // thì mới bắt đầu "nhìn" vào intersection phía trước
    private static final double APPROACHING_THRESHOLD = 80.0;

    // ── Hạ tầng ───────────────────────────────────────────────────
    private final RoadNetwork network;

    // ── Danh sách thực thể trong simulation ──────────────────────
    private final List<Vehicle>      vehicles    = new ArrayList<>();
    private final List<TrafficLight> lights      = new ArrayList<>();

    // ── Rules — tái sử dụng từ code của teammate ─────────────────
    private final List<TrafficRule>  globalRules = new ArrayList<>();

    // ── Điều hướng giao lộ ───────────────────────────────────────
    private final IntersectionNavigator intersectionNavigator;
    private final PathFollower          pathFollower = new PathFollower();

    // ═══════════════════════════════════════════════════════════
    // Constructor
    // ═══════════════════════════════════════════════════════════

    public TrafficController(RoadNetwork network) {
        if (network == null) throw new IllegalArgumentException("Network không được null");
        this.network = network;
        this.intersectionNavigator = new IntersectionNavigator(network);
    }

    // ═══════════════════════════════════════════════════════════
    // Đăng ký thực thể — API công khai
    // ═══════════════════════════════════════════════════════════

    public void spawnVehicle(RoadSegment entry, Lane lane, DriverBehavior behavior) {
        Vehicle v = new Car(...);   // hoặc loại xe khác
        v.placeOnSegment(entry, lane, 0.0);
        addVehicle(v);
    }

    public void addVehicle(Vehicle v) {
        if (v != null) vehicles.add(v);
    }

    /**
     * Đăng ký xe và đặt lên segment/lane ban đầu.
     */
    public void addVehicle(Vehicle v, RoadSegment segment, Lane lane, double t) {
        if (v == null) return;
        v.placeOnSegment(segment, lane, t);
        vehicles.add(v);
    }

    public void addLight(TrafficLight tl) { if (tl != null) lights.add(tl);     }

    // Ba method dưới đây tái sử dụng nguyên từ code của teammate
    public void addRule(TrafficRule rule)    { globalRules.add(rule);    }
    public void removeRule(TrafficRule rule) { globalRules.remove(rule); }

    public List<TrafficRule> getGlobalRules() {
        return Collections.unmodifiableList(globalRules);
    }

    // ═══════════════════════════════════════════════════════════
    // TICK — vòng lặp chính, gọi mỗi giây
    // ═══════════════════════════════════════════════════════════

    public void tick() {
        // Bước 1: Loại bỏ xe đã đi hết đường
        vehicles.removeIf(v ->
            v.getCurrentSegment() != null && v.getSegmentProgress() >= 1.0
            && findUpcomingIntersection(v, v.getPosition()) == null
        );

        // Bước 2: Cập nhật tất cả đèn giao thông trước
        // (đèn phải đổi trạng thái trước khi xe ra quyết định)
        for (TrafficLight light : lights) {
            light.tick();
        }

        // Bước 3: Chụp ảnh vị trí tất cả xe TẠI ĐẦU TICK
        // Phải làm trước vòng lặp xe — xem giải thích trong takePositionSnapshot()
        Map<Vehicle, Position> snapshot = takePositionSnapshot();

        // Bước 4: Xử lý từng xe
        for (Vehicle v : vehicles) {
            RoadContext ctx = buildContext(v, snapshot);
            processVehicle(v, ctx);
        }
    }

    // ═══════════════════════════════════════════════════════════
    // BƯỚC 2: Chụp ảnh vị trí
    // ═══════════════════════════════════════════════════════════

    /**
     * Tạo bản đồ "xe → vị trí hiện tại" trước khi bất kỳ xe nào di chuyển.
     *
     * Tại sao cần snapshot?
     * Nếu không có snapshot, xe A xử lý trước sẽ di chuyển, rồi khi
     * xe B hỏi "xe A đang ở đâu?" nó thấy vị trí SAU khi di chuyển.
     * Snapshot đảm bảo mọi xe đọc cùng một "bức ảnh" của thế giới.
     */
    private Map<Vehicle, Position> takePositionSnapshot() {
        Map<Vehicle, Position> snapshot = new HashMap<>();
        for (Vehicle v : vehicles) {
            snapshot.put(v, v.getPosition());
        }
        return Collections.unmodifiableMap(snapshot);
    }

    // ═══════════════════════════════════════════════════════════
    // BƯỚC 3A: Xây dựng RoadContext
    // ═══════════════════════════════════════════════════════════

    /**
     * Lắp ráp toàn bộ thông tin môi trường cho một xe tại tick này.
     * Đây là nơi gọi findNearbyVehicles() với logic 3 case.
     */
    private RoadContext buildContext(Vehicle subject,
                                     Map<Vehicle, Position> snapshot) {
        TrafficLightState lightState = getCurrentLightState(subject);
        List<Vehicle>     nearby     = findNearbyVehicles(subject, snapshot);

        return new RoadContext.Builder()
                .subject(subject)
                .lightState(lightState)
                .currentLane(subject.getCurrentLane())
                .currentSegment(subject.getCurrentSegment())
                .nearbyVehicles(nearby)
                .positionSnapshot(snapshot)
                .build();
    }

    // ═══════════════════════════════════════════════════════════
    // LOGIC TÌM NEARBY VEHICLES — 3 case theo topology
    // ═══════════════════════════════════════════════════════════

    /**
     * Tìm tất cả xe trong tầm ảnh hưởng của {@code subject}.
     *
     * Định nghĩa "lân cận" theo topology mạng lưới đường, không phải
     * khoảng cách Euclid thuần túy. Hai xe có thể cách nhau 5m nhưng
     * ở hai làn song song chạy cùng chiều — chúng không liên quan nhau.
     *
     * Ba case được kiểm tra theo thứ tự:
     *
     *   Case 1 — Cùng RoadSegment:
     *     Xe đi trên cùng đoạn đường, ảnh hưởng trực tiếp đến việc
     *     giữ khoảng cách, vượt xe, đổi làn của subject.
     *
     *   Case 2 — Ở Intersection subject sắp tới:
     *     Subject còn cách cuối đoạn đường dưới APPROACHING_THRESHOLD.
     *     Cần biết ai đang ở intersection phía trước để quyết định
     *     có nên nhập giao lộ không.
     *
     *   Case 3 — Cùng Intersection với subject:
     *     Subject đang trong vùng của một intersection.
     *     Cần biết các xe khác cùng đang trong giao lộ để tránh va chạm.
     *
     * @param subject  Xe đang được xét
     * @param snapshot Bản đồ vị trí đầu tick
     * @return Danh sách xe lân cận, không chứa subject, không bao giờ null
     */
    private List<Vehicle> findNearbyVehicles(Vehicle subject,
                                              Map<Vehicle, Position> snapshot) {
        Position    subjectPos     = snapshot.getOrDefault(subject, subject.getPosition());
        RoadSegment subjectSegment = subject.getCurrentSegment();

        Intersection upcoming = findUpcomingIntersection(subject, subjectPos);
        Intersection current  = subject.getCurrentIntersection();
        if (current == null) {
            current = findCurrentIntersection(subjectPos);
        }

        List<Vehicle> nearby = new ArrayList<>();

        for (Vehicle other : vehicles) {
            if (other == subject) continue;

            // ── Case 1: Cùng RoadSegment ───────────────────────────────
            // Dùng == (so sánh tham chiếu) vì mỗi đoạn đường là object
            // duy nhất trong RoadNetwork — đúng hơn và nhanh hơn equals()
            if (subjectSegment != null
                    && other.getCurrentSegment() == subjectSegment) {
                nearby.add(other);
                continue; // Đã thêm, không cần kiểm tra case 2 và 3
            }

            Position otherPos = snapshot.getOrDefault(other, other.getPosition());

            // ── Case 2: Other đang ở intersection subject sắp tới ──────
            if (upcoming != null && isAtIntersection(otherPos, upcoming)) {
                nearby.add(other);
                continue;
            }

            // ── Case 3: Cả hai đang ở cùng intersection ────────────────
            if (current != null && isAtIntersection(otherPos, current)) {
                nearby.add(other);
            }
        }

        return nearby;
    }

    /**
     * Tìm Intersection mà subject ĐANG HƯỚNG TỚI.
     *
     * Dựa vào Lane.Direction để biết xe đang đi về đầu nào:
     *   FORWARD  → xe đi từ start → end  → upcoming tại điểm END
     *   BACKWARD → xe đi từ end  → start → upcoming tại điểm START
     *
     * Chỉ trả về intersection nếu xe đủ gần điểm cuối
     * (trong vòng APPROACHING_THRESHOLD), tránh trường hợp xe ở giữa
     * đoạn đường dài đã bị coi là "sắp tới" intersection.
     *
     * Trả về null nếu xe chưa trên đường, chưa có làn, hoặc còn xa.
     */
    private Intersection findUpcomingIntersection(Vehicle subject,
                                                   Position subjectPos) {
        RoadSegment seg  = subject.getCurrentSegment();
        Lane        lane = subject.getCurrentLane();
        if (seg == null || lane == null) return null;

        // Điểm cuối theo hướng di chuyển
        double targetX, targetY;
        if (lane.getDirection() == Lane.Direction.FORWARD) {
            targetX = seg.getEndX();
            targetY = seg.getEndY();
        } else {
            targetX = seg.getStartX();
            targetY = seg.getStartY();
        }

        // Kiểm tra xe có đủ gần để quan tâm đến intersection này không
        double distToEnd = subjectPos.distanceTo(new Position(targetX, targetY));
        if (distToEnd > APPROACHING_THRESHOLD) return null;

        // findNearestIntersection dùng bán kính 60 để khớp với
        // cách SimulationView snap endpoint vào intersection khi vẽ đường
        return network.findNearestIntersection(targetX, targetY, 60.0);
    }

    /**
     * Kiểm tra subject có đang NẰM TRONG một intersection không.
     *
     * Duyệt tất cả intersection trong network và kiểm tra vị trí subject.
     * Bán kính lấy từ getRenderData().radius — cùng giá trị mà UI dùng
     * để vẽ vòng tròn nút giao, đảm bảo logic nhất quán với hiển thị.
     *
     * Trả về intersection đầu tiên chứa subject, hoặc null nếu không có.
     */
    private Intersection findCurrentIntersection(Position subjectPos) {
        for (Intersection inter : network.getIntersections()) {
            if (isAtIntersection(subjectPos, inter)) {
                return inter;
            }
        }
        return null;
    }

    /**
     * Kiểm tra vị trí {@code pos} có nằm trong vùng của {@code intersection}.
     *
     * Dùng bán kính từ getRenderData() thay vì hằng số cứng để đảm bảo
     * "vùng intersection" trong logic luôn khớp với "vùng intersection"
     * trên màn hình.
     */
    private boolean isAtIntersection(Position pos, Intersection intersection) {
        double radius = intersection.getRenderData().radius;
        double cx     = intersection.getCenterX();
        double cy     = intersection.getCenterY();
        return pos.distanceTo(new Position(cx, cy)) <= radius;
    }

    // ═══════════════════════════════════════════════════════════
    // BƯỚC 3B: Xử lý hành động
    // ═══════════════════════════════════════════════════════════

    /**
     * Cho xe đề xuất hành động → kiểm tra rule → thực thi.
     * Nếu bị từ chối, behavior tự chọn fallback và thử lại một lần.
     */
    private void processVehicle(Vehicle v, RoadContext ctx) {
        Action proposed = v.getBehavior().decideAction(v, ctx);

        if (isVehicleAllowed(v, proposed, ctx)) {
            executeAction(v, proposed);
        } else {
            Action fallback = v.getBehavior().handleRejection(v, ctx, proposed);
            executeAction(v, fallback);
        }

        tryEnterIntersection(v);
        tryExitIntersection(v);
    }

    /**
     * Khi xe đến cuối segment và đã có {@link PlannedExit}, tạo quỹ đạo cung.
     */
    private void tryEnterIntersection(Vehicle v) {
        if (v.getTravelMode() != TravelMode.ON_SEGMENT) {
            return;
        }
        if (v.getPlannedExit() == PlannedExit.NONE) {
            return;
        }
        if (v.getSegmentProgress() < 1.0) {
            return;
        }

        Intersection upcoming = intersectionNavigator.peekUpcomingIntersection(v);
        if (upcoming != null && !intersectionNavigator.canMerge(v, upcoming, vehicles)) {
            return;
        }

        IntersectionPath path = intersectionNavigator.buildPath(v);
        if (path == null) {
            System.out.printf("[CONTROLLER] Không tạo được path cho %s (exit=%s)%n",
                    v.getType(), v.getPlannedExit());
            v.clearPlannedExit();
            return;
        }

        v.enterIntersectionPath(path, path.getIntersection());
        v.clearPlannedExit();
    }

    /** Khi hoàn thành quỹ đạo cung, gán segment/lane nhánh ra. */
    private void tryExitIntersection(Vehicle v) {
        if (v.getTravelMode() != TravelMode.ON_INTERSECTION_PATH) {
            return;
        }
        if (!v.isIntersectionPathComplete()) {
            return;
        }
        intersectionNavigator.applyExit(v);
    }

    /**
     * Kiểm tra xe {@code v} có được phép thực hiện action {@code a} không.
     *
     * Tái sử dụng nguyên logic từ code của teammate:
     *   - Gộp globalRules + local rules từ context
     *   - Sort theo priority giảm dần
     *   - Bỏ qua rule không áp dụng cho loại xe này (appliesTo)
     *   - Rule đầu tiên từ chối → trả false ngay, không kiểm tra tiếp
     *
     * appliesTo() cần được thêm vào TrafficRule interface như default method.
     * Xe khẩn cấp (isEmergency = true) sẽ được xử lý trong appliesTo()
     * của từng rule — mặc định trả true, override khi cần miễn trừ.
     *
     * @param v   Xe cần kiểm tra
     * @param a   Action được đề xuất
     * @param ctx Context hiện tại của xe
     * @return true nếu được phép, false nếu bị từ chối
     */
    public boolean isVehicleAllowed(Vehicle v, Action a, RoadContext ctx) {
        // Gộp global + local rules — tái sử dụng từ teammate
        List<TrafficRule> allRules = new ArrayList<>(globalRules);
        allRules.addAll(ctx.getLocalRules());

        // Sort theo priority giảm dần — tái sử dụng từ teammate
        allRules.sort(Comparator.comparingInt(TrafficRule::getPriority).reversed());

        for (TrafficRule rule : allRules) {
            // Bỏ qua rule không áp dụng cho xe này — tái sử dụng từ teammate
            if (!rule.appliesTo(v)) continue;

            if (!rule.isAllowed(v, a, ctx)) {
                // Log khi bị từ chối — tái sử dụng từ teammate
                System.out.printf("[CONTROLLER] REJECTED: %s → %s (rule: %s)%n",
                        v.getType(), a, rule.getClass().getSimpleName());
                return false;
            }
        }
        return true;
    }

    /**
     * Thực thi action theo {@link TravelMode}.
     * Rẽ tại giao lộ qua {@link PlannedExit}, không qua {@code turnLeft}/{@code turnRight}.
     */
    private void executeAction(Vehicle v, Action action) {
        if (v.getTravelMode() == TravelMode.ON_INTERSECTION_PATH) {
            executeOnIntersectionPath(v, action);
        } else {
            executeOnSegment(v, action);
        }
    }

    private void executeOnSegment(Vehicle v, Action action) {
        switch (action) {
            case MOVE_FORWARD  -> v.advanceOnSegment();
            case ACCELERATE    -> v.accelerate();
            case SLOW_DOWN     -> v.slowDown();
            case STOP          -> v.stop();
            case CHANGE_LANE   -> v.changeLane();
            case OVERTAKE      -> { v.accelerate(); v.changeLane(); }
            case TURN_LEFT     -> v.setPlannedExit(PlannedExit.LEFT);
            case TURN_RIGHT    -> v.setPlannedExit(PlannedExit.RIGHT);
            case U_TURN        -> v.setPlannedExit(PlannedExit.LEFT);
            // RANDOM: driver gán PlannedExit.RANDOM trực tiếp qua setPlannedExit
            case HONK          -> v.honk();
            default            -> v.advanceOnSegment();
        }
    }

    private void executeOnIntersectionPath(Vehicle v, Action action) {
        switch (action) {
            case MOVE_FORWARD  -> pathFollower.advance(v);
            case ACCELERATE    -> v.accelerate();
            case SLOW_DOWN     -> v.slowDown();
            case STOP          -> v.stop();
            case HONK          -> v.honk();
            default            -> pathFollower.advance(v);
        }
    }

    // ═══════════════════════════════════════════════════════════
    // HELPER: Đèn giao thông
    // ═══════════════════════════════════════════════════════════

    /**
     * Lấy trạng thái đèn hiện tại liên quan đến xe.
     *
     * Hiện tại: dùng đèn đầu tiên trong danh sách làm placeholder.
     *
     * TODO (khi hệ thống đèn hoàn chỉnh): tìm đèn gần nhất theo
     * hướng di chuyển của xe, dùng intersection sắp tới của xe.
     * Gợi ý: findUpcomingIntersection() → lấy đèn của intersection đó.
     */
    private TrafficLightState getCurrentLightState(Vehicle subject) {
        if (lights.isEmpty()) return TrafficLightState.GREEN;
        return lights.get(0).getCurrentState();
    }
}