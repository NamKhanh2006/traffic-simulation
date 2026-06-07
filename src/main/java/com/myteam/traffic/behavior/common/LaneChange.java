// LaneChange.java
package com.myteam.traffic.behavior.common;

import com.myteam.traffic.vehicle.Vehicle;
import com.myteam.traffic.context.RoadContext;

/**
 * Utility class for lane-change manoeuvres with MOBIL safety check.
 */
public class LaneChange {
    private LaneChange() {}

    // MOBIL parameters
    private static final double INCENTIVE_THRESHOLD = 0.2;   // lợi ích tối thiểu (m/s²)
    private static final double POLITENESS_FACTOR = 0.5;     // quan tâm đến xe sau
    private static final double MAX_SAFE_BRAKING = 2.0;       // phanh tối đa cho phép (m/s²)

    /**
     * Kiểm tra an toàn theo MOBIL trước khi đổi làn.
     * @param self xe muốn đổi làn
     * @param context ngữ cảnh (cần cung cấp rearOld, rearNew, targetLane)
     * @param targetLane làn đích
     * @return true nếu được phép đổi làn
     */
    public static boolean isLaneChangeSafeMOBIL(Vehicle self, RoadContext context, int targetLane) {
        Vehicle rearOld = context.getRearVehicleOnCurrentLane();   // xe sau trên làn cũ
        Vehicle rearNew = context.getRearVehicleOnLane(targetLane); // xe sau trên làn mới

        // Giả sử có phương thức getAcceleration() trong Vehicle
        double aSelfOld = self.getAcceleration();
        double aSelfNew = context.getPredictedAccelerationOnLane(self, targetLane);
        double aRearOld = (rearOld != null) ? rearOld.getAcceleration() : 0;
        double aRearNew = (rearNew != null) ? rearNew.getAcceleration() : 0;

        // Safety condition: xe sau trên làn mới không bị phanh quá mức
        double brakingNew = aRearNew - (rearNew != null ? rearNew.getDecelerationLimit() : -MAX_SAFE_BRAKING);        if (brakingNew > 0) return false;
        if (brakingNew > 0) return false;
        
        // Incentive condition: lợi ích tổng thể > ngưỡng
        double deltaSelf = aSelfNew - aSelfOld;
        double deltaRearOld = aRearOld - (rearOld != null ? rearOld.getAccelerationAfterSelfLeaves() : 0);
        double deltaRearNew = aRearNew - (rearNew != null ? rearNew.getAccelerationAfterSelfJoins() : 0);
        double totalBenefit = deltaSelf + POLITENESS_FACTOR * (deltaRearOld + deltaRearNew);
        return totalBenefit > INCENTIVE_THRESHOLD;
    }

    private static double estimateAcceleration(Vehicle v, Vehicle leader) {
        return DistanceKeeping.idmAcceleration(v, leader);
    }

    private static double estimateAccelerationOnLane(Vehicle self, RoadContext context, int targetLane) {
        Vehicle frontOnTarget = context.getFrontVehicleOnLane(targetLane);
        return DistanceKeeping.idmAcceleration(self, frontOnTarget);
    }
    /**
     * Đổi làn chỉ khi an toàn theo MOBIL.
     */
    public static void changeLaneIfSafe(Vehicle v, RoadContext context, int targetLane) {
        if (isLaneChangeSafeMOBIL(v, context, targetLane)) {
            v.changeLane();
        }
    }

    public static void changeLane(Vehicle v) {
        v.changeLane();
    }
}
