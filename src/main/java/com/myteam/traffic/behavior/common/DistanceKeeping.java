// DistanceKeeping.java
package com.myteam.traffic.behavior.common;

import com.myteam.traffic.vehicle.Vehicle;

/**
 * Utility class for safe following-distance logic with TTC and IDM.
 */
public class DistanceKeeping {
    private DistanceKeeping() {}

    // Ngưỡng an toàn theo thời gian (giây) – dùng TTC
    public static final double SAFE_TTC = 2.0;      // TTC an toàn
    public static final double MIN_TTC = 1.0;       // Dưới ngưỡng này phải can thiệp

    // IDM parameters
    private static final double DEFAULT_MAX_ACCEL = 2.0;
    private static final double DEFAULT_COMFORT_DECEL = 3.0;
    private static final double MIN_GAP = 2.0;
    private static final double DESIRED_TIME_HEADWAY = 1.5;
    
    /**
     * Tính Time-to-Collision với xe phía trước.
     * @return TTC (giây), hoặc POSITIVE_INFINITY nếu không có xe trước hoặc v_rel <= 0
     */
    public static double timeToCollision(Vehicle self, Vehicle front) {
        if (front == null) return Double.POSITIVE_INFINITY;
        double gap = front.getX() - self.getX();
        double relSpeed = self.getSpeed() - front.getSpeed();
        if (relSpeed <= 0) return Double.POSITIVE_INFINITY; // không đuổi kịp
        return gap / relSpeed;
    }

    public static double timeHeadway(Vehicle self, Vehicle front) {
        if (front == null) return Double.POSITIVE_INFINITY;
        double gap = front.getX() - self.getX();
        double speed = self.getSpeed();
        if (speed <= 0) return Double.POSITIVE_INFINITY;
        return gap / speed;
    }

    public static boolean isImminentCollision(Vehicle self, Vehicle front) {
        return timeToCollision(self, front) < CRITICAL_TTC;
    }

    public static boolean isHeadwayUnsafe(Vehicle self, Vehicle front) {
        return timeHeadway(self, front) < DESIRED_TIME_HEADWAY;
    }

    @Deprecated
    public static Action keepDistance(Vehicle self, Vehicle front) {
        if (front == null) return null;
        double gap = front.getX() - self.getX();
        if (gap < 5.0) {
            self.slowDown();
            return Action.SLOW_DOWN;
        }
        return null;
    }
    /**
     * Kiểm tra an toàn dựa trên TTC.
     * @return Action cần thực hiện nếu không an toàn, null nếu an toàn
     */
    public static Action keepDistanceWithTTC(Vehicle self, Vehicle front) {
        if (front == null) return null;
        double ttc = timeToCollision(self, front);
        if (ttc < MIN_TTC) {
            self.slowDown();
            System.out.printf("%s SLOW_DOWN (TTC=%.2fs)%n", self.getType(), ttc);
            return Action.SLOW_DOWN;
        }
        if (ttc < SAFE_TTC) {
            System.out.printf("%s TTC below safe (%.2fs), prepare to brake%n", 
                              self.getType(), ttc);
            self.slowDown(0.5);  // giảm tốc nhẹ
            return Action.SLOW_DOWN;
        }
        return null;
    }

    /**
     * Intelligent Driver Model – tính gia tốc mong muốn.
     * @param self xe chủ thể
     * @param front xe phía trước (có thể null)
     * @param aMax gia tốc tối đa (m/s²)
     * @param comfortableDecel giảm tốc thoải mái (m/s²)
     * @return gia tốc mong muốn (có thể âm)
     */
    public static double idmAcceleration(Vehicle self, Vehicle front, 
                                         double aMax, double comfortableDecel) {
        double v = self.getSpeed();
        double v0 = self.getMaxSpeed();
        
        if (front == null) {
            // free road: tăng tốc về maxSpeed
            return aMax * (1 - Math.pow(v / v0, 4));
        }

        double v = self.getSpeed();
        double v0 = self.getMaxSpeed();
        double deltaV = v - front.getSpeed();
        double gap = front.getX() - self.getX();
        double s0 = 2.0;          // khoảng cách dừng tối thiểu (m)
        double T = 1.5;           // thời gian an toàn mong muốn (s)

        double sStar = s0 + Math.max(0, v * T + (v * deltaV) / (2 * Math.sqrt(aMax * comfortableDecel)));
        double accel = aMax * (1 - Math.pow(v / v0, 4) - Math.pow(sStar / gap, 2));
        return Math.max(-comfortableDecel, Math.min(aMax, accel));
    }
    public static double idmAcceleration(Vehicle self, Vehicle front) {
        return idmAcceleration(self, front, DEFAULT_MAX_ACCEL, DEFAULT_COMFORT_DECEL);
    }

}
