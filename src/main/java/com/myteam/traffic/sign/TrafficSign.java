/*
package com.myteam.traffic.sign;
import com.myteam.traffic.model.infrastructure.*;
import com.myteam.traffic.common.*;
import com.myteam.traffic.rule.*;

public class TrafficSign {
	private float xPos;	// The x position of the sign
	private float yPos;	// The y position of the sign
	private String code; // Use float to handle multiple signs with the same integer code
	/*
	Eg: code = 110.0 => Sign P.110
	 	code = 120.1 => Sign P.120a
	 	code = 120.2 => Sign P.120b
		code = 120.3 => Sign P.120c
		code = 120.4 => Sign P.120d
		(Note that these codes are only examples to illustrate the pattern and do not necessarily belong to real 
		traffic signs)
	*/
/*
	private SignType type;
	private TrafficRule rule;
	
	RoadSegment road; // The road segment on which the sign is put

	public TrafficSign(float xPos, float yPos, String code, TrafficRule rule, RoadSegment road) {
		super();
		this.xPos = xPos;
		this.yPos = yPos;
		this.code = code;
		this.rule = rule;
		this.road = road;
	}

	// Getters and setters
	
	
	
	
}
*/

package com.myteam.traffic.sign;

import com.myteam.traffic.model.geometry.Direction;
import com.myteam.traffic.model.geometry.Position;
import com.myteam.traffic.rule.*;
import com.myteam.traffic.vehicle.VehicleType;

import java.util.HashSet;
import java.util.Set;

/**
 * Một biển báo giao thông đặt tại một vị trí trên bản đồ.
 *
 * <p>Mỗi biển:</p>
 * <ul>
 *   <li>Có một loại ({@link SignType}) xác định ngữ nghĩa.</li>
 *   <li>Chỉ có tác dụng với xe di chuyển theo {@code applicableDirection}
 *       (ví dụ: biển cấm rẽ trái chỉ áp dụng cho xe đang đến từ hướng Đông).</li>
 *   <li>Chỉ có tác dụng khi xe trong bán kính {@code effectRadius}.</li>
 *   <li>Ánh xạ sang một {@link TrafficRule} để {@code TrafficController}
 *       kiểm tra — thông qua {@code RoadContext.getLocalRules()}.</li>
 * </ul>
 *
 * <p>Tạo qua các factory method để đảm bảo rule được cấu hình đúng:</p>
 * <pre>
 *   // Biển giới hạn tốc độ 50km/h:
 *   TrafficSign sign = TrafficSign.speedLimit(50, position, Direction.EAST, 80.0);
 *
 *   // Biển cấm còi:
 *   TrafficSign sign = TrafficSign.noHorn(position, Direction.NORTH, 60.0);
 *
 *   // Biển cấm xe tải (trên 3.5 tấn):
 *   TrafficSign sign = TrafficSign.maxWeight(3500, position, Direction.EAST, 80.0);
 * </pre>
 */
public class TrafficSign {

    // =========================================================
    // Fields
    // =========================================================

    private final SignType  type;
    private final Position  position;

    /**
     * Hướng xe đang đi mà biển này áp dụng.
     * Ví dụ: biển đặt bên đường hướng Đông → Tây thì {@code applicableDirection = WEST}.
     * null = áp dụng cho tất cả hướng.
     */
    private final Direction applicableDirection;

    /**
     * Bán kính tác dụng (pixels / đơn vị bản đồ).
     * {@code ContextBuilder} lọc ra các biển trong bán kính này khi build context.
     */
    private final double effectRadius;

    /**
     * Luật giao thông mà biển này áp đặt.
     * Được {@code RoadContext.getLocalRules()} trả về và
     * {@code TrafficController} áp dụng khi kiểm tra xe.
     */
    private final TrafficRule rule;

    // =========================================================
    // Constructor (private — dùng Builder hoặc factory)
    // =========================================================

    private TrafficSign(Builder builder) {
        this.type                = builder.type;
        this.position            = builder.position;
        this.applicableDirection = builder.applicableDirection;
        this.effectRadius        = builder.effectRadius;
        this.rule                = builder.rule;
    }

    // =========================================================
    // Factory methods — tạo nhanh các loại biển phổ biến
    // =========================================================

    /**
     * Biển giới hạn tốc độ.
     *
     * @param maxSpeedKmh    Tốc độ tối đa (km/h).
     * @param pos            Vị trí đặt biển.
     * @param dir            Hướng xe mà biển áp dụng (null = mọi hướng).
     * @param effectRadius   Bán kính tác dụng.
     */
    public static TrafficSign speedLimit(double maxSpeedKmh, Position pos,
                                         Direction dir, double effectRadius) {
        TrafficRule rule = new SpeedRule(null, maxSpeedKmh, null);
        return new Builder(SignType.SPEED_LIMIT, pos, rule)
                .applicableDirection(dir)
                .effectRadius(effectRadius)
                .build();
    }

    /**
     * Biển cấm còi.
     */
    public static TrafficSign noHorn(Position pos, Direction dir, double effectRadius) {
        TrafficRule rule = new HornRule(false, null); // mustHorn=false → cấm còi
        return new Builder(SignType.NO_HORN, pos, rule)
                .applicableDirection(dir)
                .effectRadius(effectRadius)
                .build();
    }

    /**
     * Biển cấm vượt — áp dụng cho tất cả xe.
     */
    public static TrafficSign noOvertaking(Position pos, Direction dir, double effectRadius) {
        Set<Action> banned = new HashSet<>();
        banned.add(Action.OVERTAKE);
        TrafficRule rule = new ActionRule(null, banned, null);
        return new Builder(SignType.NO_OVERTAKING, pos, rule)
                .applicableDirection(dir)
                .effectRadius(effectRadius)
                .build();
    }

    /**
     * Biển cấm rẽ trái.
     */
    public static TrafficSign noLeftTurn(Position pos, Direction dir, double effectRadius) {
        Set<Action> banned = new HashSet<>();
        banned.add(Action.TURN_LEFT);
        TrafficRule rule = new ActionRule(null, banned, null);
        return new Builder(SignType.NO_LEFT_TURN, pos, rule)
                .applicableDirection(dir)
                .effectRadius(effectRadius)
                .build();
    }

    /**
     * Biển cấm rẽ phải.
     */
    public static TrafficSign noRightTurn(Position pos, Direction dir, double effectRadius) {
        Set<Action> banned = new HashSet<>();
        banned.add(Action.TURN_RIGHT);
        TrafficRule rule = new ActionRule(null, banned, null);
        return new Builder(SignType.NO_RIGHT_TURN, pos, rule)
                .applicableDirection(dir)
                .effectRadius(effectRadius)
                .build();
    }

    /**
     * Biển cấm quay đầu.
     */
    public static TrafficSign noUTurn(Position pos, Direction dir, double effectRadius) {
        Set<Action> banned = new HashSet<>();
        banned.add(Action.U_TURN);
        TrafficRule rule = new ActionRule(null, banned, null);
        return new Builder(SignType.NO_U_TURN, pos, rule)
                .applicableDirection(dir)
                .effectRadius(effectRadius)
                .build();
    }

    /**
     * Biển giới hạn tải trọng.
     *
     * @param maxWeightKg Tải trọng tối đa (kg).
     */
    public static TrafficSign maxWeight(double maxWeightKg, Position pos,
                                        Direction dir, double effectRadius) {
        TrafficRule rule = new DimensionLimitRule(maxWeightKg, null, null, null, null);
        return new Builder(SignType.MAX_WEIGHT, pos, rule)
                .applicableDirection(dir)
                .effectRadius(effectRadius)
                .build();
    }

    /**
     * Biển giới hạn chiều cao.
     *
     * @param maxHeightM Chiều cao tối đa (mét).
     */
    public static TrafficSign maxHeight(double maxHeightM, Position pos,
                                        Direction dir, double effectRadius) {
        TrafficRule rule = new DimensionLimitRule(null, maxHeightM, null, null, null);
        return new Builder(SignType.MAX_HEIGHT, pos, rule)
                .applicableDirection(dir)
                .effectRadius(effectRadius)
                .build();
    }

    /**
     * Biển khu vực trường học — tốc độ tối đa 30km/h, cấm còi.
     * Tạo ra biển kép: 1 biển tốc độ + 1 biển cấm còi.
     * Trả về biển tốc độ; gọi thêm {@code noHorn()} để đặt biển cấm còi.
     */
    public static TrafficSign schoolZone(Position pos, Direction dir, double effectRadius) {
        TrafficRule rule = new SpeedRule(null, 30.0, null);
        return new Builder(SignType.SCHOOL_ZONE, pos, rule)
                .applicableDirection(dir)
                .effectRadius(effectRadius)
                .build();
    }

    /**
     * Biển khu vực bệnh viện — cấm còi với tất cả xe.
     */
    public static TrafficSign hospitalZone(Position pos, Direction dir, double effectRadius) {
        TrafficRule rule = new HornRule(false, null);
        return new Builder(SignType.HOSPITAL_ZONE, pos, rule)
                .applicableDirection(dir)
                .effectRadius(effectRadius)
                .build();
    }

    // =========================================================
    // Logic nghiệp vụ
    // =========================================================

    /**
     * Kiểm tra biển này có áp dụng cho xe đang đi theo hướng {@code vehicleDir} không.
     *
     * <p>Nếu {@code applicableDirection} là null → áp dụng mọi hướng.
     * Dùng dung sai ±30° để tránh sai số tọa độ nhỏ.</p>
     */
    public boolean appliesTo(Direction vehicleDir) {
        if (applicableDirection == null) return true;
        return applicableDirection.isApproximately(vehicleDir, 30.0);
    }

    /**
     * Trả về rule mà biển này áp đặt.
     * Được {@code RoadContext.getLocalRules()} gọi để đưa vào danh sách
     * kiểm tra của {@code TrafficController}.
     */
    public TrafficRule getRule() {
        return rule;
    }

    // =========================================================
    // Getters
    // =========================================================

    public SignType  getType()                 { return type; }
    public Position  getPosition()             { return position; }
    public Direction getApplicableDirection()  { return applicableDirection; }
    public double    getEffectRadius()         { return effectRadius; }

    @Override
    public String toString() {
        return String.format("TrafficSign[%s at %s, dir=%s, r=%.0f]",
                type, position, applicableDirection, effectRadius);
    }

    // =========================================================
    // Builder
    // =========================================================

    public static class Builder {
        private final SignType      type;
        private final Position      position;
        private final TrafficRule   rule;
        private Direction           applicableDirection = null;
        private double              effectRadius        = 60.0; // default 60 đơn vị

        /** type, position, rule là bắt buộc. */
        public Builder(SignType type, Position position, TrafficRule rule) {
            this.type     = type;
            this.position = position;
            this.rule     = rule;
        }

        public Builder applicableDirection(Direction dir) {
            this.applicableDirection = dir;
            return this;
        }

        public Builder effectRadius(double radius) {
            this.effectRadius = radius;
            return this;
        }

        public TrafficSign build() {
            return new TrafficSign(this);
        }
    }
}
