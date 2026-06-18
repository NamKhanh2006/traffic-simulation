package com.myteam.traffic.context;

import com.myteam.traffic.model.infrastructure.Lane;
import com.myteam.traffic.model.infrastructure.RoadSegment;
import com.myteam.traffic.model.infrastructure.HighwaySegment;
import com.myteam.traffic.behavior.common.Action;
import com.myteam.traffic.rule.ActionRule;
import com.myteam.traffic.rule.TrafficRule;
import com.myteam.traffic.light.TrafficLightState;
import com.myteam.traffic.marking.RoadMarking;
import com.myteam.traffic.model.geometry.Position;
import com.myteam.traffic.model.geometry.Direction;
import com.myteam.traffic.navigation.IntersectionPath;
import com.myteam.traffic.vehicle.PlannedExit;
import com.myteam.traffic.vehicle.TravelMode;
import com.myteam.traffic.vehicle.Vehicle;
import com.myteam.traffic.vehicle.VehicleType;

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

    /**
     * Luật gắn với ngữ cảnh hiện tại (làn / đoạn đường), không phải global.
     * Được tính một lần khi build — immutable.
     */
    private final List<TrafficRule> localRules;

    private final double deltaTime;
    private final Map<Vehicle, Double> pathProgressSnapshot;
    private final Map<Vehicle, Double> speedSnapshot;

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
        this.localRules       = buildLocalRules(builder);
        this.deltaTime        = builder.deltaTime;
        this.pathProgressSnapshot = Collections.unmodifiableMap(new HashMap<>(builder.pathProgressSnapshot));
        this.speedSnapshot = Collections.unmodifiableMap(new HashMap<>(builder.speedSnapshot));
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
    public double           getDeltaTime()       { return deltaTime;        }
    public Optional<Double> getSnapshotPathProgress(Vehicle v) {
        return Optional.ofNullable(pathProgressSnapshot.get(v));
    }
    public Optional<Double> getSnapshotSpeed(Vehicle v) {
        return Optional.ofNullable(speedSnapshot.get(v));
    }

    /**
     * Luật áp dụng theo làn / đoạn đường hiện tại của subject.
     * {@link com.myteam.traffic.controller.TrafficController} gộp với global rules
     * trước khi gọi {@code isAllowed}.
     *
     * @return danh sách không null, không sửa được
     */
    public List<TrafficRule> getLocalRules() {
        return localRules;
    }

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
        if (subject.getTravelMode() == TravelMode.ON_INTERSECTION_PATH) {
            return isTooCloseOnIntersectionPath();
        }

        Position myPos = getSubjectPosition();

        double minDist = nearbyVehicles.stream()
                .filter(other -> other != subject)
                .filter(other -> isAheadOfSubject(other))
                .mapToDouble(other -> {
                    Position otherPos = positionSnapshot.getOrDefault(
                            other, other.getPosition());
                    return myPos.distanceTo(otherPos);
                })
                .min()
                .orElse(Double.MAX_VALUE);

        return minDist < SAFE_DISTANCE;
    }

    /**
     * Trên quỹ đạo giao lộ: xe cùng intersection phía trước theo chiều cung và quá gần.
     */
    public boolean isTooCloseOnIntersectionPath() {
        if (subject.getTravelMode() != TravelMode.ON_INTERSECTION_PATH) {
            return false;
        }

        IntersectionPath path = subject.getActivePath();
        if (path == null || subject.getCurrentIntersection() == null) {
            return false;
        }

        double minDist = nearbyVehicles.stream()
                .filter(other -> other != subject)
                // Chỉ xét các xe đang chạy trên CÙNG MỘT quỹ đạo Bezier với xe hiện tại
                .filter(other -> other.getActivePath() == path)
                // Xe phía trước là xe có tiến trình (PathProgress) lớn hơn xe hiện tại
                .filter(other -> other.getPathProgress() > subject.getPathProgress())
                .mapToDouble(other -> {
                    Position otherPos = positionSnapshot.getOrDefault(other, other.getPosition());
                    return getSubjectPosition().distanceTo(otherPos);
                })
                .min()
                .orElse(Double.MAX_VALUE);

        return minDist < SAFE_DISTANCE;
    }

    /**
     * Xe trên segment sắp vào giao lộ phải nhường xe đã ở trên cung (ưu tiên vòng xuyến).
     */
    public boolean mustYieldToIntersectionTraffic() {
        if (subject.getTravelMode() != TravelMode.ON_SEGMENT) {
            return false;
        }
        if (subject.getPlannedExit() == PlannedExit.NONE) {
            return false;
        }

        Position myPos = getSubjectPosition();
        return nearbyVehicles.stream()
                .filter(other -> other != subject)
                .filter(other -> other.getTravelMode() == TravelMode.ON_INTERSECTION_PATH)
                .anyMatch(other -> {
                    Position otherPos = positionSnapshot.getOrDefault(
                            other, other.getPosition());
                    return myPos.distanceTo(otherPos) < SAFE_DISTANCE;
                });
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

    // Thêm vào class RoadContext, sau các convenience methods (khoảng dòng 200-250)
    public boolean hasEmergencyApproachingFromBehind() {
        if (subject.getTravelMode() != TravelMode.ON_SEGMENT) return false;
        
        Position myPos = getSubjectPosition();
        return nearbyVehicles.stream()
            .filter(other -> other != subject && other.isEmergency())
            .filter(other -> other.getCurrentSegment() == subject.getCurrentSegment())
            .anyMatch(other -> {
                Position otherPos = positionSnapshot.getOrDefault(other, other.getPosition());
                
                // Kiểm tra xem 'other' có đang đi cùng chiều không
                if (other.getCurrentLane() == null || subject.getCurrentLane() == null) return false;
                if (other.getCurrentLane().getDirection() != subject.getCurrentLane().getDirection()) return false;
                
                // CỰC KỲ QUAN TRỌNG: Chỉ nhường đường nếu xe ưu tiên đang đi CÙNG LÀN.
                // Nếu khác làn thì không cần thiết dạt sang (để tránh lạng lách qua lại).
                if (other.getCurrentLane() != subject.getCurrentLane()) return false;

                // Tính khoảng cách dọc theo đường. Nếu positive, otherPos là ở phía sau myPos.
                double gap = otherPos.distanceAlongDirection(subject.getDirection(), myPos);
                
                // gap > 0 nghĩa là myPos ở phía trước otherPos (tức là other đang ở phía sau)
                return gap > 0 && gap < 150.0;
            });
    }

    // =========================================================
    // Tìm xe phía trước gần nhất
    // =========================================================

    /**
     * Tìm phương tiện gần nhất ở phía trước subject trên cùng làn đường.
     * 
     * <p>Luồng xử lý:
     * <ol>
     *   <li>Nếu subject đang ở chế độ {@link TravelMode#ON_SEGMENT}: tìm xe cùng {@code currentSegment}
     *       và cùng {@code currentLane}, ở phía trước subject theo hướng di chuyển, có khoảng cách
     *       dương nhỏ nhất.</li>
     *   <li>Nếu subject đang ở chế độ {@link TravelMode#ON_INTERSECTION_PATH}: tìm xe cùng
     *       {@code currentIntersection} và cùng {@code activePath}, ở phía trước theo chiều cung tròn.</li>
     *   <li>Trả về {@code null} nếu không tìm thấy xe nào phía trước.</li>
     * </ol>
     * 
     * <p><b>Lưu ý:</b> Method này dùng {@code positionSnapshot} để đảm bảo tính nhất quán
     * trong cùng một tick, không gọi {@code vehicle.getPosition()} trực tiếp.
     *
     * @return Vehicle gần nhất phía trước, hoặc null nếu không có
     */
    public Vehicle getNearestFrontVehicle() {
        if (subject.getTravelMode() == TravelMode.ON_SEGMENT) {
            return findNearestFrontOnSegment();
        } else if (subject.getTravelMode() == TravelMode.ON_INTERSECTION_PATH) {
            return findNearestFrontOnPath();
        }
        return null;
    }

    /**
     * Tìm xe phía trước gần nhất khi subject đang chạy trên RoadSegment.
     */
    private Vehicle findNearestFrontOnSegment() {
        RoadSegment seg = subject.getCurrentSegment();
        Lane lane = subject.getCurrentLane();
        if (seg == null || lane == null) {
            return null;
        }

        Position myPos = getSubjectPosition();
        Direction myDir = subject.getDirection();
        int myLaneIndex = lane.getIndex();

        Vehicle nearest = null;
        double minGap = Double.MAX_VALUE;

        for (Vehicle other : nearbyVehicles) {
            if (other == subject) continue;
            
            // Chỉ xét xe cùng segment và cùng làn
            if (other.getCurrentSegment() != seg) continue;
            if (other.getCurrentLane() == null) continue;
            if (other.getCurrentLane().getIndex() != myLaneIndex) continue;

            Position otherPos = positionSnapshot.getOrDefault(other, other.getPosition());
            
            // Kiểm tra xe có ở phía trước không (dùng dot product)
            if (!myPos.isAheadInDirection(myDir, otherPos)) continue;

            // Tính khoảng cách dọc theo hướng di chuyển (không phải Euclidean)
            double gap = myPos.distanceAlongDirection(myDir, otherPos);
            if (gap > 0 && gap < minGap) {
                minGap = gap;
                nearest = other;
            }
        }

        return nearest;
    }

    /**
     * Tìm xe phía trước gần nhất khi subject đang chạy trên IntersectionPath (cung tròn qua giao lộ).
     */
    private Vehicle findNearestFrontOnPath() {
        IntersectionPath path = subject.getActivePath();
        if (path == null || subject.getCurrentIntersection() == null) {
            return null;
        }

        double myProgress = subject.getPathProgress();
        Vehicle nearest = null;
        double minProgressGap = Double.MAX_VALUE;

        for (Vehicle other : nearbyVehicles) {
            if (other == subject) continue;

            // Phải chạy trên CÙNG quỹ đạo mới tính là đi phía trước
            if (other.getActivePath() != path) continue;

            double otherProgress = other.getPathProgress();
            double gap = otherProgress - myProgress;

            // Nếu gap > 0 nghĩa là xe kia đang nằm phía trước mũi xe mình
            if (gap > 0 && gap < minProgressGap) {
                minProgressGap = gap;
                nearest = other;
            }
        }

        return nearest;
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
    public double distanceAfterAction(Vehicle v, Action action) {
        if (v.getTravelMode() == TravelMode.ON_INTERSECTION_PATH && v.getActivePath() != null) {
            return distanceAfterActionOnPath(v, action);
        }

        Position currentPos = positionSnapshot.getOrDefault(v, v.getPosition());
        if (currentPos == null) {
            return Double.MAX_VALUE;
        }

        double displacement = estimateDisplacement(v, action);
        Direction dir = v.getDirection();
        Position projected = currentPos.project(dir, displacement);

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

    private double distanceAfterActionOnPath(Vehicle v, Action action) {
        IntersectionPath path = v.getActivePath();
        if (path == null) {
            return Double.MAX_VALUE;
        }

        double projectedS = v.getPathProgress() + estimateDisplacement(v, action);
        double[] projected = path.sampleAt(projectedS);
        Position projectedPos = new Position(projected[0], projected[1]);

        return nearbyVehicles.stream()
                .filter(other -> other != v)
                // Chỉ xét xe cùng quỹ đạo
                .filter(other -> other.getActivePath() == path)
                // Phải nằm phía trước
                .filter(other -> other.getPathProgress() > v.getPathProgress())
                .mapToDouble(other -> {
                    Position otherPos = positionSnapshot.getOrDefault(other, other.getPosition());
                    return projectedPos.distanceTo(otherPos);
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

    private static double angleAroundCenter(Position pos, double cx, double cy) {
        return Math.atan2(pos.getY() - cy, pos.getX() - cx);
    }

    /**
     * {@code other} có nằm phía trước {@code myAngle} theo chiều quét {@code sweepRad} không.
     */
    private static boolean isAheadOnArc(double myAngle, double otherAngle, double sweepRad) {
        double delta = otherAngle - myAngle;
        while (delta <= -Math.PI) {
            delta += 2 * Math.PI;
        }
        while (delta > Math.PI) {
            delta -= 2 * Math.PI;
        }
        if (sweepRad > 0) {
            return delta > 0 && delta < Math.PI;
        }
        if (sweepRad < 0) {
            return delta < 0 && delta > -Math.PI;
        }
        return false;
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
    // Local rules — suy ra từ làn / đoạn đường
    // =========================================================

    private static List<TrafficRule> buildLocalRules(Builder builder) {
        List<TrafficRule> rules = new ArrayList<>(builder.explicitLocalRules);

        Lane lane = builder.currentLane;
        if (lane != null) {
            ActionRule movementRule = movementRuleForLane(lane);
            if (movementRule != null) {
                rules.add(movementRule);
            }
        }

        RoadSegment segment = builder.currentSegment;
        if (segment instanceof HighwaySegment highway
                && lane != null
                && highway.hasEmergencyLane()
                && highway.isEmergencyLane(lane.getIndex())) {
            ActionRule emergencyLaneRule = emergencyLaneRule();
            if (emergencyLaneRule != null) {
                rules.add(emergencyLaneRule);
            }
        }

        return Collections.unmodifiableList(rules);
    }

    /**
     * Cấm các {@link Action} không khớp {@link Lane.Movement} được phép trên làn.
     * Vạch kẻ / đổi làn vẫ do {@link com.myteam.traffic.rule.MarkingRule} (global).
     */
    private static ActionRule movementRuleForLane(Lane lane) {
        Set<Lane.Movement> allowed = lane.getAllowedMovements();
        if (allowed.isEmpty()) {
            return null;
        }

        HashSet<Action> banned = new HashSet<>();
        if (!allowed.contains(Lane.Movement.U_TURN)) {
            banned.add(Action.U_TURN);
        }
        if (!allowed.contains(Lane.Movement.LEFT)) {
            banned.add(Action.TURN_LEFT);
        }
        if (!allowed.contains(Lane.Movement.RIGHT)) {
            banned.add(Action.TURN_RIGHT);
        }
        if (!allowed.contains(Lane.Movement.STRAIGHT)) {
            banned.add(Action.MOVE_FORWARD);
            banned.add(Action.ACCELERATE);
            banned.add(Action.OVERTAKE);
        }

        if (banned.isEmpty()) {
            return null;
        }
        return new ActionRule(null, banned, null);
    }

    /** Làn dừng khẩn cấp: xe thường không được lưu thông; xe EMERGENCY vẫn được. */
    private static ActionRule emergencyLaneRule() {
        HashSet<Action> banned = new HashSet<>(EnumSet.of(
                Action.MOVE_FORWARD,
                Action.ACCELERATE,
                Action.OVERTAKE,
                Action.CHANGE_LANE,
                Action.TURN_LEFT,
                Action.TURN_RIGHT,
                Action.U_TURN
        ));
        HashSet<VehicleType> nonEmergency = new HashSet<>(EnumSet.of(
                VehicleType.CAR,
                VehicleType.MOTORBIKE,
                VehicleType.BICYCLE
        ));
        return new ActionRule(null, banned, nonEmergency);
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
        private List<TrafficRule>      explicitLocalRules = new ArrayList<>();
        private double                 deltaTime        = 0.0;
        private Map<Vehicle, Double> pathProgressSnapshot = new HashMap<>();
        private Map<Vehicle, Double> speedSnapshot = new HashMap<>();


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

        public Builder deltaTime(double dt){
            this.deltaTime = dt;
            return this;
        }

        /**
         * Thêm luật cục bộ tùy chỉnh (vd. rule gắn sẵn trên {@link RoadSegment} sau này).
         * Các rule suy ra từ {@code currentLane} / cao tốc vẫn được gộp thêm khi {@code build()}.
         */
        public Builder localRules(List<TrafficRule> rules) {
            this.explicitLocalRules = rules != null
                    ? new ArrayList<>(rules)
                    : new ArrayList<>();
            return this;
        }

        public Builder addLocalRule(TrafficRule rule) {
            if (rule != null) {
                this.explicitLocalRules.add(rule);
            }
            return this;
        }

        public Builder pathProgressSnapshot(Map<Vehicle, Double> map) {
            this.pathProgressSnapshot = map != null ? map : new HashMap<>();
            return this;
        }
        
        public Builder speedSnapshot(Map<Vehicle, Double> map) {
            this.speedSnapshot = map != null ? map : new HashMap<>();
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