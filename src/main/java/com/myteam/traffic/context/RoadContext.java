package com.myteam.traffic.context;

import com.myteam.traffic.model.infrastructure.Lane;
import com.myteam.traffic.model.infrastructure.RoadSegment;
import com.myteam.traffic.behavior.common.Action;
import com.myteam.traffic.light.TrafficLightState;
import com.myteam.traffic.marking.RoadMarking;
import com.myteam.traffic.model.geometry.Position;
import com.myteam.traffic.model.geometry.Direction;
import com.myteam.traffic.vehicle.Vehicle;

import java.util.*;

/**
 * Snapshot bất biến (immutable) mô tả môi trường giao thông
 * xung quanh một phương tiện TẠI MỘT THỜI ĐIỂM trong tick hiện tại.
 *
 * ┌──────────────────────────────────────────────────────────┐
 * │  LUỒNG HOẠT ĐỘNG MỖI TICK                               │
 * │                                                          │
 * │  TrafficController                                       │
 * │       │                                                  │
 * │       ├─ chụp ảnh vị trí tất cả xe (positionSnapshot)   │
 * │       │                                                  │
 * │       └─ với mỗi xe v:                                   │
 * │            │                                             │
 * │            ├─ tạo RoadContext(v, snapshot, ...)          │
 * │            ├─ action = behavior.decideAction(v, ctx)     │
 * │            ├─ nếu !isAllowed(action) → handleRejection() │
 * │            └─ thực thi action                            │
 * └──────────────────────────────────────────────────────────┘
 *
 * Tại sao cần snapshot thay vì gọi v.getPosition() trực tiếp?
 * → Nếu xe A di chuyển trước, rồi xe B đọc vị trí xe A,
 *   xe B sẽ thấy vị trí SAU KHI di chuyển — không công bằng.
 *   Snapshot đảm bảo mọi xe đọc cùng một "bức ảnh" đầu tick.
 */
public class RoadContext {

    // =========================================================
    // Khoảng cách an toàn tối thiểu giữa hai xe (world units)
    // =========================================================
    public static final double SAFE_DISTANCE = 15.0;

    // =========================================================
    // Fields — tất cả final, không thể thay đổi sau khi build
    // =========================================================

    /** Xe mà context này được tạo cho (subject). */
    private final Vehicle subject;

    /** Trạng thái đèn giao thông tại nút giao phía trước. */
    private final TrafficLightState lightState;

    /** Vạch kẻ đường trên đoạn đường hiện tại. */
    private final List<RoadMarking> markings;

    /** Làn đường mà subject đang chạy. */
    private final Lane currentLane;

    /** Đoạn đường mà subject đang chạy. */
    private final RoadSegment currentSegment;

    /** Danh sách các xe trong tầm ảnh hưởng (cùng đoạn đường / làn đường). */
    private final List<Vehicle> nearbyVehicles;

    /**
     * Bảng vị trí của TẤT CẢ xe tại đầu tick.
     * Mọi phép tính đều phải dùng bảng này, không gọi v.getPosition() trực tiếp.
     */
    private final Map<Vehicle, Position> positionSnapshot;

    // =========================================================
    // Constructor — private, chỉ được tạo qua Builder
    // =========================================================

    private RoadContext(Builder builder) {
        this.subject          = builder.subject;
        this.lightState       = builder.lightState;
        this.markings         = Collections.unmodifiableList(new ArrayList<>(builder.markings));
        this.currentLane      = builder.currentLane;
        this.currentSegment   = builder.currentSegment;
        this.nearbyVehicles   = Collections.unmodifiableList(new ArrayList<>(builder.nearbyVehicles));
        this.positionSnapshot = Collections.unmodifiableMap(new HashMap<>(builder.positionSnapshot));
    }

    // =========================================================
    // Getters cơ bản
    // =========================================================

    public Vehicle          getSubject()         { return subject;          }
    public TrafficLightState getLightState()      { return lightState;       }
    public List<RoadMarking> getMarkings()        { return markings;         }
    public Lane             getCurrentLane()      { return currentLane;      }
    public RoadSegment      getCurrentSegment()   { return currentSegment;   }
    public List<Vehicle>    getNearbyVehicles()   { return nearbyVehicles;   }

    /**
     * Lấy vị trí snapshot của một xe bất kỳ.
     * Trả về Optional.empty() nếu xe chưa có trong snapshot (xe mới sinh).
     */
    public Optional<Position> getSnapshotPosition(Vehicle v) {
        return Optional.ofNullable(positionSnapshot.get(v));
    }

    // =========================================================
    // Convenience methods — dùng bởi DriverBehavior để ra quyết định
    // =========================================================

    /**
     * Đèn tại nút giao phía trước có đang đỏ không?
     *
     * NormalDriver dùng để quyết định dừng hay đi:
     *   if (ctx.hasRedLightAhead()) return Action.STOP;
     */
    public boolean hasRedLightAhead() {
        return lightState == TrafficLightState.RED;
    }

    /**
     * Đèn có đang xanh không?
     */
    public boolean isGreenLight() {
        return lightState == TrafficLightState.GREEN;
    }

    /**
     * Có xe nào đang ở PHÍA TRƯỚC subject không?
     *
     * Cách hoạt động:
     *   1. Lọc qua tất cả nearbyVehicles (trừ chính subject)
     *   2. Với mỗi xe, kiểm tra xem nó có nằm phía trước subject không
     *      (dùng dot product theo hướng di chuyển của subject)
     *   3. Trả về true nếu tìm thấy ít nhất 1 xe như vậy
     */
    public boolean hasFrontVehicle() {
        return nearbyVehicles.stream()
                .filter(other -> other != subject)
                .anyMatch(other -> isAheadOfSubject(other));
    }

    /**
     * Xe phía trước có đang QUÁN GẦN (dưới SAFE_DISTANCE) không?
     *
     * Cách hoạt động:
     *   1. Lọc các xe ở phía trước subject
     *   2. Tính khoảng cách từ subject đến từng xe đó (dùng snapshot)
     *   3. Lấy khoảng cách nhỏ nhất
     *   4. So sánh với SAFE_DISTANCE (15 world units)
     *
     * NormalDriver dùng để quyết định giảm tốc:
     *   if (ctx.isTooCloseToFront()) return Action.SLOW_DOWN;
     */
    public boolean isTooCloseToFront() {
        Position myPos = getSubjectPosition();

        double minDist = nearbyVehicles.stream()
                .filter(other -> other != subject)
                .filter(other -> isAheadOfSubject(other))
                .mapToDouble(other -> {
                    // Lấy vị trí xe kia từ snapshot, fallback về getPosition()
                    Position otherPos = positionSnapshot.getOrDefault(
                            other, other.getPosition());
                    return myPos.distanceTo(otherPos);
                })
                .min()
                .orElse(Double.MAX_VALUE); // Không có xe phía trước → cực kỳ xa

        return minDist < SAFE_DISTANCE;
    }

    /**
     * Có xe khẩn cấp (cứu thương, cứu hỏa, cảnh sát) nào GẦN ĐÂY không?
     *
     * NormalDriver dùng để nhường đường:
     *   if (ctx.hasEmergencyNearby()) return Action.CHANGE_LANE;
     */
    public boolean hasEmergencyNearby() {
        return nearbyVehicles.stream()
                .filter(other -> other != subject)
                .anyMatch(Vehicle::isEmergency);
    }

    /**
     * Subject có đang CHẠY QUÁ CHẬM so với quy định cao tốc không?
     *
     * Chỉ có ý nghĩa khi currentSegment là HighwaySegment.
     * Dùng bởi AI để biết cần tăng tốc.
     */
    public boolean isBelowHighwayMinSpeed() {
        if (currentSegment instanceof com.myteam.traffic.model.infrastructure.HighwaySegment hwy) {
            return hwy.isBelowMinSpeed(subject.getSpeed());
        }
        return false;
    }

    /**
     * Có xe nào đi PHÍA TRƯỚC subject không? (Phiên bản cho AggressiveDriver)
     * Tương đương hasFrontVehicle() nhưng tên rõ nghĩa hơn trong context aggressive.
     */
    public boolean hasFrontVehicleAhead() {
        return hasFrontVehicle();
    }

    // =========================================================
    // Logic tính khoảng cách — dùng bởi DistanceRule
    // =========================================================

    /**
     * Ước tính khoảng cách từ subject đến xe gần nhất phía trước,
     * SAU KHI subject thực hiện hành động {@code action}.
     *
     * Cách hoạt động:
     *   1. Lấy vị trí hiện tại của subject từ snapshot
     *   2. Tính vị trí dự kiến dựa trên action (ACCELERATE → đi xa hơn, STOP → đứng yên)
     *   3. Với mỗi xe phía trước, tính khoảng cách từ vị trí dự kiến đến xe đó
     *   4. Trả về khoảng cách nhỏ nhất
     *
     * DistanceRule dùng để kiểm tra:
     *   if (ctx.distanceAfterAction(v, a) < minDistance) → KHÔNG cho phép
     *
     * @param v      Xe cần kiểm tra (thường là subject)
     * @param action Hành động muốn thực hiện
     * @return Khoảng cách tối thiểu đến xe phía trước sau khi thực hiện action
     */
    public double DistanceAfterAction(Vehicle v, Action action) {
        Position currentPos = positionSnapshot.getOrDefault(v, v.getPosition());
        if (currentPos == null) return Double.MAX_VALUE;

        // Ước tính khoảng di chuyển dựa vào action
        double displacement = estimateDisplacement(v, action);

        // Vị trí dự kiến của v sau khi thực hiện action
        Direction dir = v.getDirection();
        Position projected = currentPos.project(dir, displacement);

        // Tìm xe gần nhất phía trước v, tính từ vị trí dự kiến
        return nearbyVehicles.stream()
                .filter(other -> other != v)
                .filter(other -> isAheadOf(v, other))
                .mapToDouble(other -> {
                    Position otherPos = positionSnapshot.getOrDefault(
                            other, other.getPosition());
                    return projected.distanceTo(otherPos);
                })
                .min()
                .orElse(Double.MAX_VALUE);
    }

    /**
     * Ước tính khoảng di chuyển (world units) tương ứng với một action.
     *
     * Bảng quy đổi:
     *   ACCELERATE    → speed + 2  (tăng tốc, đi xa hơn)
     *   MOVE_FORWARD  → speed      (giữ nguyên tốc độ)
     *   SLOW_DOWN     → speed - 2  (giảm tốc)
     *   STOP          → 0          (đứng yên)
     *   CHANGE_LANE   → speed      (giả sử tốc độ không đổi khi đổi làn)
     *   OVERTAKE      → speed + 3  (vượt xe cần tăng tốc nhiều hơn)
     *   các action khác → speed    (mặc định)
     */
    private double estimateDisplacement(Vehicle v, Action action) {
        double spd = v.getSpeed();
        return switch (action) {
            case ACCELERATE   -> spd + 2.0;
            case MOVE_FORWARD -> spd;
            case SLOW_DOWN    -> Math.max(0, spd - 2.0);
            case STOP         -> 0.0;
            case OVERTAKE     -> spd + 3.0;
            default           -> spd;
        };
    }

    // =========================================================
    // Helper — dùng nội bộ
    // =========================================================

    /**
     * Lấy vị trí hiện tại của subject từ snapshot.
     * Fallback về getPosition() nếu subject chưa có trong snapshot.
     */
    private Position getSubjectPosition() {
        return positionSnapshot.getOrDefault(subject, subject.getPosition());
    }

    /**
     * Kiểm tra xe {@code other} có đang ở PHÍA TRƯỚC subject không.
     *
     * Thuật toán dùng dot product:
     *   - Tính vector từ subject → other
     *   - Tính dot product với vector hướng đi của subject
     *   - Nếu dot > 0 → other nằm cùng chiều → phía trước
     *
     * Ví dụ trực quan:
     *   subject đi về phía Đông (→), other ở phía Đông → dot > 0 → phía trước ✓
     *   subject đi về phía Đông (→), other ở phía Tây → dot < 0 → phía sau  ✗
     */
    private boolean isAheadOfSubject(Vehicle other) {
        return isAheadOf(subject, other);
    }

    /**
     * Kiểm tra {@code other} có đang ở phía trước {@code reference} không.
     * Phiên bản tổng quát hơn isAheadOfSubject() — dùng được cho mọi xe.
     */
    private boolean isAheadOf(Vehicle reference, Vehicle other) {
        Position refPos   = positionSnapshot.getOrDefault(reference, reference.getPosition());
        Position otherPos = positionSnapshot.getOrDefault(other, other.getPosition());
        Direction dir     = reference.getDirection();
        return refPos.isAheadInDirection(dir, otherPos);
    }

    // =========================================================
    // Kiểm tra điều kiện giao thông nâng cao
    // =========================================================

    /**
     * Xe có BẮT BUỘC phải dừng không?
     * (Đèn đỏ HOẶC đang gần vạch dừng)
     */
    public boolean mustStop() {
        return lightState == TrafficLightState.RED;
    }

    /**
     * Xe có phải nhường đường không?
     * Dùng bởi SignalRule khi đèn vàng hoặc biển nhường đường.
     */
    public boolean mustGiveWay(Vehicle v) {
        return lightState == TrafficLightState.YELLOW;
    }

    /**
     * Xe có thể tiến vào nút giao an toàn không?
     * Điều kiện: đèn xanh VÀ không có xe nào quá gần phía trước.
     */
    public boolean canProceedSafely(Vehicle v) {
        return lightState == TrafficLightState.GREEN && !isTooCloseToFront();
    }

    // =========================================================
    // Builder
    // =========================================================

    /**
     * Builder để tạo RoadContext.
     *
     * Ví dụ sử dụng (trong TrafficController):
     * <pre>
     *   RoadContext ctx = new RoadContext.Builder()
     *       .subject(vehicle)
     *       .lightState(TrafficLightState.RED)
     *       .currentLane(lane)
     *       .currentSegment(segment)
     *       .nearbyVehicles(nearbyList)
     *       .positionSnapshot(snapshot)
     *       .build();
     * </pre>
     */
    public static class Builder {

        // Bắt buộc
        private Vehicle subject = null;

        // Tùy chọn — có giá trị mặc định an toàn
        private TrafficLightState      lightState       = TrafficLightState.GREEN;
        private List<RoadMarking>      markings         = new ArrayList<>();
        private Lane                   currentLane      = null;
        private RoadSegment            currentSegment   = null;
        private List<Vehicle>          nearbyVehicles   = new ArrayList<>();
        private Map<Vehicle, Position> positionSnapshot = new HashMap<>();

        /** Xe mà context này mô tả môi trường xung quanh. BẮT BUỘC phải set. */
        public Builder subject(Vehicle v) {
            this.subject = v;
            return this;
        }

        public Builder lightState(TrafficLightState state) {
            this.lightState = state;
            return this;
        }

        public Builder markings(List<RoadMarking> markings) {
            this.markings = markings != null ? markings : new ArrayList<>();
            return this;
        }

        public Builder currentLane(Lane lane) {
            this.currentLane = lane;
            return this;
        }

        public Builder currentSegment(RoadSegment segment) {
            this.currentSegment = segment;
            return this;
        }

        public Builder nearbyVehicles(List<Vehicle> vehicles) {
            this.nearbyVehicles = vehicles != null ? vehicles : new ArrayList<>();
            return this;
        }

        public Builder positionSnapshot(Map<Vehicle, Position> snapshot) {
            this.positionSnapshot = snapshot != null ? snapshot : new HashMap<>();
            return this;
        }

        /**
         * Tạo RoadContext.
         * @throws IllegalStateException nếu subject chưa được set.
         */
        public RoadContext build() {
            if (subject == null) {
                throw new IllegalStateException(
                    "RoadContext.Builder: subject (xe) không được null. " +
                    "Hãy gọi .subject(vehicle) trước khi build()."
                );
            }
            return new RoadContext(this);
        }
    }
}