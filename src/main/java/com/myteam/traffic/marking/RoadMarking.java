/*
package com.myteam.traffic.marking;

import com.myteam.traffic.model.geometry.Geometry;
import com.myteam.traffic.model.infrastructure.RoadSegment;
import com.myteam.traffic.model.infrastructure.Lane;

import java.util.List;

public class RoadMarking {

    private MarkingType type;
    private Geometry geometry;
    private RoadSegment road;
    private List<Lane> relatedLanes; // optional
    
    private boolean isCrossingAllowed;

    public RoadMarking(MarkingType type, Geometry geometry, RoadSegment road, boolean isCrossingAllowed) {
        this.type = type;
        this.geometry = geometry;
        this.road = road;
        this.isCrossingAllowed = isCrossingAllowed;
    }

    /*
    public boolean isVehicleCrossing(Vehicle v) {
        return geometry.intersects(v);
    }
    */
/*
    public MarkingType getType() {
        return type;
    }
    
    public boolean getIsCrossingAllowed() {
    	return isCrossingAllowed;
    }
}
*/

/*
package com.myteam.traffic.marking;
import com.myteam.traffic.common.*;
import com.myteam.traffic.model.infrastructure.*;

public class RoadMarking {
	private MarkingType type;
	private float startX;
	private float startY;
	private float endX;
	private float endY;
	
	private Lane[] lanes = new Lane[2]; // A road marking is always between two lanes
	
}
*/

package com.myteam.traffic.marking;

import com.myteam.traffic.model.geometry.Direction;
import com.myteam.traffic.model.geometry.Position;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Một vạch kẻ đường, được biểu diễn như một đoạn thẳng trên mặt đường.
 *
 * <p>Mỗi vạch có:</p>
 * <ul>
 *   <li>Loại ({@link MarkingType}) — xác định ngữ nghĩa giao thông.</li>
 *   <li>Hình học — điểm đầu/cuối để {@code GeometryUtils.segmentsIntersect()}
 *       kiểm tra xe có cắt qua không.</li>
 *   <li>Kiểu nét ({@code solid}) — nét liền = không được cắt,
 *       nét đứt = được cắt (dùng cho LANE_DIVIDER, CENTER_LINE).</li>
 *   <li>Hướng cho phép ({@code allowedDirections}) — chỉ dùng cho
 *       {@code DIRECTION_ARROW}, null với các loại khác.</li>
 * </ul>
 *
 * <p>Dùng bởi {@code MarkingRule}: kiểm tra xe có cắt qua vạch
 * không được phép cắt không. Các trường hợp phức tạp hơn
 * (STOP_LINE phụ thuộc đèn đỏ) do {@code SignalRule} xử lý riêng.</p>
 *
 * <p>Tạo qua Builder hoặc các factory method có sẵn:</p>
 * <pre>
 *   // Nét liền — cấm vượt:
 *   RoadMarking solid = RoadMarking.solidCenterLine(new Position(0,100), new Position(200,100));
 *
 *   // Nét đứt — cho phép chuyển làn:
 *   RoadMarking dashed = RoadMarking.dashedLaneDivider(new Position(0,50), new Position(200,50));
 *
 *   // Mũi tên chỉ hướng:
 *   RoadMarking arrow = RoadMarking.directionArrow(start, end, Set.of(Direction.NORTH, Direction.EAST));
 * </pre>
 */
public class RoadMarking {

    // =========================================================
    // Fields
    // =========================================================

    private final MarkingType type;

    /** Điểm đầu của đoạn vạch (hệ tọa độ màn hình). */
    private final Position start;

    /** Điểm cuối của đoạn vạch. */
    private final Position end;

    /**
     * true  = nét liền (cấm cắt qua — LANE_DIVIDER, CENTER_LINE).
     * false = nét đứt (cho phép cắt qua — thay làn hợp lệ).
     * Không có ý nghĩa với STOP_LINE, PEDESTRIAN, DIRECTION_ARROW, EDGE_LINE.
     */
    private final boolean solid;

    /**
     * Tập hướng đi được phép khi qua vạch này.
     * Chỉ dùng với {@code DIRECTION_ARROW}; null với các loại khác.
     */
    private final Set<Direction> allowedDirections;

    // =========================================================
    // Constructor (private — dùng Builder hoặc factory)
    // =========================================================

    private RoadMarking(Builder builder) {
        this.type               = builder.type;
        this.start              = builder.start;
        this.end                = builder.end;
        this.solid              = builder.solid;
        this.allowedDirections  = builder.allowedDirections == null
                ? null
                : Collections.unmodifiableSet(new HashSet<>(builder.allowedDirections));
    }

    // =========================================================
    // Factory methods — tạo nhanh các loại vạch phổ biến
    // =========================================================

    /**
     * Vạch tim đường nét liền — cấm vượt tuyệt đối.
     */
    public static RoadMarking solidCenterLine(Position start, Position end) {
        return new Builder(MarkingType.CENTER_LINE, start, end).solid(true).build();
    }

    /**
     * Vạch tim đường nét đứt — cho phép vượt khi an toàn.
     */
    public static RoadMarking dashedCenterLine(Position start, Position end) {
        return new Builder(MarkingType.CENTER_LINE, start, end).solid(false).build();
    }

    /**
     * Vạch phân làn nét liền — cấm chuyển làn.
     */
    public static RoadMarking solidLaneDivider(Position start, Position end) {
        return new Builder(MarkingType.LANE_DIVIDER, start, end).solid(true).build();
    }

    /**
     * Vạch phân làn nét đứt — cho phép chuyển làn.
     */
    public static RoadMarking dashedLaneDivider(Position start, Position end) {
        return new Builder(MarkingType.LANE_DIVIDER, start, end).solid(false).build();
    }

    /**
     * Vạch dừng xe — xe phải dừng trước vạch khi đèn đỏ.
     * Bản thân vạch không cấm cắt qua; {@code SignalRule} xử lý logic đèn đỏ.
     */
    public static RoadMarking stopLine(Position start, Position end) {
        return new Builder(MarkingType.STOP_LINE, start, end).solid(false).build();
    }

    /**
     * Vạch dành cho người đi bộ (vạch zebra).
     */
    public static RoadMarking pedestrianCrossing(Position start, Position end) {
        return new Builder(MarkingType.PEDESTRIAN, start, end).solid(false).build();
    }

    /**
     * Mũi tên chỉ hướng — xe chỉ được đi theo các hướng trong {@code allowed}.
     */
    public static RoadMarking directionArrow(Position start, Position end,
                                             Set<Direction> allowed) {
        return new Builder(MarkingType.DIRECTION_ARROW, start, end)
                .solid(false)
                .allowedDirections(allowed)
                .build();
    }

    /**
     * Vạch mép đường — cấm xe vượt ra ngoài.
     */
    public static RoadMarking edgeLine(Position start, Position end) {
        return new Builder(MarkingType.EDGE_LINE, start, end).solid(true).build();
    }

    // =========================================================
    // Logic nghiệp vụ — dùng bởi MarkingRule
    // =========================================================

    /**
     * Kiểm tra xe có được phép cắt qua vạch này không.
     *
     * <ul>
     *   <li>{@code CENTER_LINE} / {@code LANE_DIVIDER}: nét liền → cấm, nét đứt → được.</li>
     *   <li>{@code EDGE_LINE}: luôn cấm.</li>
     *   <li>{@code STOP_LINE}: bản thân vạch không cấm — {@code SignalRule} xử lý riêng.</li>
     *   <li>{@code PEDESTRIAN}: không cấm xe cắt qua — logic người đi bộ xử lý riêng.</li>
     *   <li>{@code DIRECTION_ARROW}: không cấm cắt qua — hướng đi kiểm tra qua
     *       {@link #isDirectionAllowed(Direction)}.</li>
     * </ul>
     */
    public boolean isCrossingAllowed() {
        switch (type) {
            case CENTER_LINE:
            case LANE_DIVIDER:
                return !solid;          // nét đứt → được phép
            case EDGE_LINE:
                return false;           // mép đường — không bao giờ được phép
            case STOP_LINE:
            case PEDESTRIAN:
            case DIRECTION_ARROW:
            default:
                return true;            // logic phức tạp hơn do rule khác xử lý
        }
    }

    /**
     * Kiểm tra hướng đi {@code dir} có hợp lệ theo vạch mũi tên này không.
     * Chỉ có ý nghĩa với {@code DIRECTION_ARROW}; các loại khác luôn trả {@code true}.
     */
    public boolean isDirectionAllowed(Direction dir) {
        if (type != MarkingType.DIRECTION_ARROW || allowedDirections == null)
            return true;
        // Dùng isApproximately để chấp nhận sai lệch nhỏ về góc (±15°)
        return allowedDirections.stream()
                .anyMatch(allowed -> allowed.isApproximately(dir, 15.0));
    }

    // =========================================================
    // Getters
    // =========================================================

    public MarkingType getType()                    { return type; }
    public Position    getStart()                   { return start; }
    public Position    getEnd()                     { return end; }
    public boolean     isSolid()                    { return solid; }
    public Set<Direction> getAllowedDirections()     { return allowedDirections; }

    @Override
    public String toString() {
        return String.format("RoadMarking[%s, %s, from=%s to=%s]",
                type, solid ? "solid" : "dashed", start, end);
    }

    // =========================================================
    // Builder
    // =========================================================

    public static class Builder {
        private final MarkingType type;
        private final Position start;
        private final Position end;
        private boolean         solid              = false;
        private Set<Direction>  allowedDirections  = null;

        /** type, start, end là bắt buộc. */
        public Builder(MarkingType type, Position start, Position end) {
            this.type  = type;
            this.start = start;
            this.end   = end;
        }

        public Builder solid(boolean solid) {
            this.solid = solid;
            return this;
        }

        public Builder allowedDirections(Set<Direction> dirs) {
            this.allowedDirections = dirs;
            return this;
        }

        public RoadMarking build() {
            return new RoadMarking(this);
        }
    }
}