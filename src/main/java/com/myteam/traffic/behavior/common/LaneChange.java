package com.myteam.traffic.behavior.common;

import com.myteam.traffic.vehicle.VehicleType;
import com.myteam.traffic.model.infrastructure.Lane;
import com.myteam.traffic.rule.MarkingRule;

/**
 * Utility class for lane-change manoeuvres with MOBIL safety check.
 * Follows Strategic Design Pattern - contains ONLY calculation logic.
 */
public final class LaneChange {
    private LaneChange() {}

    // MOBIL parameters
    private static final double INCENTIVE_THRESHOLD = 0.2;   // minimum benefit (m/s²)
    private static final double POLITENESS_FACTOR = 0.5;     // consideration for following vehicles
    private static final double MAX_SAFE_BRAKING = 2.0;      // maximum allowed braking (m/s²)

    /**
     * Checks lane change safety using MOBIL model.
     * 
     * @param subjectSpeed       Speed of subject vehicle (m/s)
     * @param subjectMaxSpeed    Max speed of subject vehicle (m/s)
     * @param leaderOldSpeed     Speed of leader in current lane (m/s) or -1 if none
     * @param gapToLeaderOld     Distance to leader in current lane (m)
     * @param leaderNewSpeed     Speed of leader in target lane (m/s) or -1 if none
     * @param gapToLeaderNew     Distance to leader in target lane (m)
     * @param followerNewSpeed   Speed of follower in target lane (m/s) or -1 if none
     * @param gapToFollowerNew   Distance to follower in target lane (m)
     * @return true if lane change is safe and beneficial
     */
    public static boolean isLaneChangeSafeMOBIL(
        double subjectSpeed,
        double subjectMaxSpeed,
        double leaderOldSpeed,
        double gapToLeaderOld,
        double leaderNewSpeed,
        double gapToLeaderNew,
        double followerNewSpeed,
        double gapToFollowerNew
    ) {
        // Calculate accelerations using pure math
        double aSelfOld = DistanceKeeping.calculateIDMAcceleration(
            subjectSpeed, subjectMaxSpeed, leaderOldSpeed, gapToLeaderOld
        );
        
        double aSelfNew = DistanceKeeping.calculateIDMAcceleration(
            subjectSpeed, subjectMaxSpeed, leaderNewSpeed, gapToLeaderNew
        );
        
        double aFollowerNew = (followerNewSpeed >= 0) ? 
            DistanceKeeping.calculateIDMAcceleration(
                followerNewSpeed, subjectMaxSpeed, subjectSpeed, gapToFollowerNew
            ) : 0;

        double aFollowerNewCurrent = (followerNewSpeed >= 0 && leaderNewSpeed >= 0) ? 
            DistanceKeeping.calculateIDMAcceleration(
                followerNewSpeed, subjectMaxSpeed, leaderNewSpeed, gapToFollowerNew + gapToLeaderNew
            ) : 0;

        // Safety condition: follower shouldn't need to brake too hard
        if (aFollowerNew - aFollowerNewCurrent > MAX_SAFE_BRAKING) {
            return false;
        }
        
        // Incentive condition: total benefit > threshold
        double deltaSelf = aSelfNew - aSelfOld;
        double deltaFollower = aFollowerNew - aFollowerNewCurrent;
        double totalBenefit = deltaSelf + POLITENESS_FACTOR * deltaFollower;
        
        return totalBenefit > INCENTIVE_THRESHOLD;
    }

    /**
     * Checks if lane change is physically possible based on lane markings.
     * 
     * @param currentLane     Current lane object
     * @param targetLaneIndex Index of target lane
     * @param vehicleType     Type of vehicle
     * @param isEmergency     If vehicle is in emergency mode
     * @return true if lane change is physically allowed
     */
        public static boolean isLaneChangePhysicallyPossible(
        Lane currentLane,
        int targetLaneIndex,
        VehicleType vehicleType,
        boolean isEmergency
    ) {
        int currentIndex = currentLane.getIndex();
        Lane.VehicleCategory cat = toVehicleCategory(vehicleType);
        if (targetLaneIndex == currentIndex - 1) {
            return currentLane.canCrossLeft(cat, isEmergency);
        } else if (targetLaneIndex == currentIndex + 1) {
            return currentLane.canCrossRight(cat, isEmergency);
        }
        return false;
    }

    private static Lane.VehicleCategory toVehicleCategory(VehicleType type) {
        if (type == null) return Lane.VehicleCategory.CAR;
        return switch (type) {
            case CAR -> Lane.VehicleCategory.CAR;
            case MOTORBIKE -> Lane.VehicleCategory.MOTORBIKE;
            case BICYCLE -> Lane.VehicleCategory.BICYCLE;
            case AMBULANCE, FIRETRUCK -> Lane.VehicleCategory.EMERGENCY;
            default -> Lane.VehicleCategory.CAR;
        };
    }
}
