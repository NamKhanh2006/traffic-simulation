/*
package com.myteam.traffic.context;

import com.myteam.traffic.model.infrastructure.*;
import com.myteam.traffic.rule.*;
import com.myteam.traffic.sign.*;
import com.myteam.traffic.marking.RoadMarking;

import java.util.*;

public class RoadContext {

    private boolean redLight;
    private List<RoadMarking> markings = new ArrayList<>();
    private List<TrafficSign> signs = new ArrayList<>();
    private Lane currentLane;  // The Lane that the vehicle is currently on (each vehicle builds its own context)

    public boolean isRedLight() {
        return redLight;
    }

    public void setRedLight(boolean redLight) {
        this.redLight = redLight;
    }

    public List<RoadMarking> getMarkings() {
        return markings;
    }

    public void addMarking(RoadMarking m) {
        markings.add(m);
    }
    
    public List<TrafficRule> getLocalRules() {
        List<TrafficRule> localRules = new ArrayList<>();
        
        // Lấy luật từ biển báo trong context
        for (TrafficSign sign : this.signs) {
            if (sign.getRule() != null)
            	localRules.add(sign.getRule());
            // TODO: Write getRule() method to get the rule that a sign implements
        }
        
        // Lấy luật từ làn đường hiện tại
        if (this.currentLane != null) {
            localRules.addAll(this.currentLane.getRules());
            // TODO: Write the getRules() method for the Lane class to get rules applied on a lane
        }
        
        return localRules;
    }
    
}
*/

package com.myteam.traffic.context;

import com.myteam.traffic.model.infrastructure.*;
import com.myteam.traffic.rule.*;
import com.myteam.traffic.sign.*;
import com.myteam.traffic.marking.RoadMarking;
import com.myteam.traffic.vehicle.Vehicle;
import com.myteam.traffic.common.*;
import com.myteam.traffic.light.*;

import java.util.*;

/**
 * Snapshot bất biến (immutable) mô tả môi trường giao thông
 * xung quanh một phương tiện tại một thời điểm trong tick hiện tại.
 *
 * Mỗi RoadContext được tạo một lần đầu tick cho từng xe,
 * dựa trên snapshot vị trí toàn bộ xe — đảm bảo tính nhất quán
 * khi nhiều xe được xử lý tuần tự trong cùng một tick.
 *
 * Cách tạo (qua Builder):
 * <pre>
 *   RoadContext ctx = new RoadContext.Builder()
 *       .lightState(TrafficLightState.RED)
 *       .currentLane(lane)
 *       .currentSegment(segment)
 *       .markings(markings)
 *       .signs(signs)
 *       .nearbyVehicles(nearbyList)
 *       .positionSnapshot(snapshot)
 *       .build();
 * </pre>
 */
public class RoadContext {

    // =========================================================
    // Trạng thái hạ tầng (Infrastructure state)
    // =========================================================

    /** Trạng thái đèn giao thông hiện tại (RED / YELLOW / GREEN). */
    private final TrafficLightState lightState;

    /** Vạch kẻ đường trên đoạn đường hiện tại. */
    private final List<RoadMarking> markings;

    /** Biển báo giao thông trên đoạn đường hiện tại. */
    private final List<TrafficSign> signs;

    /** Làn đường mà xe đang chạy. */
    private final Lane currentLane;

    /** Đoạn đường mà xe đang chạy. */
    private final RoadSegment currentSegment;

    // =========================================================
    // Trạng thái động (Dynamic state — từ snapshot đầu tick)
    // =========================================================

    /**
     * Danh sách các xe trong tầm ảnh hưởng (cùng làn, cùng đoạn đường).
     * Dùng để tính khoảng cách, kiểm tra xung đột.
     */
    private final List<Vehicle> nearbyVehicles;

    /**
     * Vị trí của tất cả xe tại đầu tick (snapshot bất biến).
     * Mọi rule phải dùng bản đồ này thay vì gọi v.getPosition() trực tiếp
     * để tránh kết quả không nhất quán giữa các xe trong cùng một tick.
     */
    private final Map<Vehicle, Position> positionSnapshot;

    // =========================================================
    // Constructor (private — chỉ tạo qua Builder)
    // =========================================================

    private RoadContext(Builder builder) {
        this.lightState       = builder.lightState;
        this.markings         = Collections.unmodifiableList(new ArrayList<>(builder.markings));
        this.signs            = Collections.unmodifiableList(new ArrayList<>(builder.signs));
        this.currentLane      = builder.currentLane;
        this.currentSegment   = builder.currentSegment;
        this.nearbyVehicles   = Collections.unmodifiableList(new ArrayList<>(builder.nearbyVehicles));
        this.positionSnapshot = Collections.unmodifiableMap(new HashMap<>(builder.positionSnapshot));
    }

    // =========================================================
    // Getters — Trạng thái hạ tầng
    // =========================================================

    /** Trả về true nếu đèn đang đỏ. Dùng bởi SignalRule. */
    public boolean isRedLight() {
        return lightState == TrafficLightState.RED;
    }

    /** Trả về true nếu đèn đang xanh. */
    public boolean isGreenLight() {
        return lightState == TrafficLightState.GREEN;
    }

    /** Trả về trạng thái đèn đầy đủ (RED / YELLOW / GREEN). */
    public TrafficLightState getLightState() {
        return lightState;
    }

    public List<RoadMarking> getMarkings()   { return markings; }
    public List<TrafficSign> getSigns()      { return signs; }
    public Lane getCurrentLane()             { return currentLane; }
    public RoadSegment getCurrentSegment()   { return currentSegment; }

    // =========================================================
    // Getters — Trạng thái động
    // =========================================================

    /** Danh sách các xe lân cận (cùng đoạn đường / làn đường). */
    public List<Vehicle> getNearbyVehicles() {
        return nearbyVehicles;
    }

    /**
     * Lấy vị trí snapshot của một xe.
     * Trả về Optional.empty() nếu xe không có trong snapshot
     * (ví dụ: xe mới xuất hiện trong tick này).
     */
    public Optional<Position> getSnapshotPosition(Vehicle v) {
        return Optional.ofNullable(positionSnapshot.get(v));
    }

    // =========================================================
    // Logic tính toán — dùng bởi các Rule
    // =========================================================

    /**
     * Tính khoảng cách từ xe {@code v} đến xe gần nhất phía trước,
     * giả sử {@code v} thực hiện hành động {@code a}.
     *
     * <p>Phương thức này dùng vị trí snapshot để đảm bảo nhất quán
     * trong tick. Được gọi bởi {@link DistanceRule}.</p>
     *
     * @param v Xe đang được kiểm tra.
     * @param a Hành động mà xe muốn thực hiện.
     * @return Khoảng cách sau khi thực hiện action,
     *         hoặc {@code Double.MAX_VALUE} nếu không có xe phía trước.
     */
    public double distanceAfterAction(Vehicle v, Action a) {
        Position current = positionSnapshot.get(v);
        if (current == null) return Double.MAX_VALUE;

        // Tính vị trí dự kiến của v sau khi thực hiện action
        Position projected = current.project(v.getDirection(), a.getDisplacement());

        return nearbyVehicles.stream()
            .filter(other -> other != v)
            .filter(other -> isAhead(v, other))    // Chỉ xét xe phía trước v
            .mapToDouble(other -> {
                // Dùng snapshot; fallback về getPosition() nếu không có
                Position otherPos = positionSnapshot.getOrDefault(other, other.getPosition());
                return projected.distanceTo(otherPos);
            })
            .min()
            .orElse(Double.MAX_VALUE);
    }

    /**
     * Kiểm tra xem xe {@code other} có đang ở phía trước xe {@code subject}
     * theo hướng di chuyển hiện tại của {@code subject} không.
     */
    private boolean isAhead(Vehicle subject, Vehicle other) {
        Position subjectPos = positionSnapshot.getOrDefault(subject, subject.getPosition());
        Position otherPos   = positionSnapshot.getOrDefault(other,   other.getPosition());
        return subjectPos.isAheadInDirection(subject.getDirection(), otherPos);
    }

    // =========================================================
    // Tổng hợp Local Rules từ context
    // =========================================================

    /**
     * Trả về danh sách các luật cục bộ được suy ra từ context này:
     * biển báo trên đoạn đường và quy tắc riêng của làn đường.
     *
     * <p>Danh sách này được {@link com.myteam.traffic.controller.TrafficController}
     * gộp vào global rules trước khi kiểm tra.</p>
     */
    public List<TrafficRule> getLocalRules() {
        List<TrafficRule> localRules = new ArrayList<>();

        for (TrafficSign sign : signs) {
            TrafficRule rule = sign.getRule();
            if (rule != null) localRules.add(rule);
        }

        if (currentLane != null) {
            localRules.addAll(currentLane.getRules());
        }

        return localRules;
    }

    // =========================================================
    // Builder
    // =========================================================

    /**
     * Builder để tạo RoadContext.
     *
     * <p>Tất cả field đều có giá trị mặc định an toàn,
     * chỉ cần set những field cần thiết.</p>
     */
    public static class Builder {

        // Giá trị mặc định an toàn
        private TrafficLightState       lightState       = TrafficLightState.GREEN;
        private List<RoadMarking>       markings         = new ArrayList<>();
        private List<TrafficSign>       signs            = new ArrayList<>();
        private Lane                    currentLane      = null;
        private RoadSegment             currentSegment   = null;
        private List<Vehicle>           nearbyVehicles   = new ArrayList<>();
        private Map<Vehicle, Position>  positionSnapshot = new HashMap<>();

        public Builder lightState(TrafficLightState state) {
            this.lightState = state;
            return this;
        }

        public Builder markings(List<RoadMarking> markings) {
            this.markings = markings;
            return this;
        }

        public Builder signs(List<TrafficSign> signs) {
            this.signs = signs;
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
            this.nearbyVehicles = vehicles;
            return this;
        }

        public Builder positionSnapshot(Map<Vehicle, Position> snapshot) {
            this.positionSnapshot = snapshot;
            return this;
        }

        public RoadContext build() {
            return new RoadContext(this);
        }
    }
}