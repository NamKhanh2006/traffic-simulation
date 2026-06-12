package com.myteam.traffic.behavior.common;

import com.myteam.traffic.model.infrastructure.Lane;
import com.myteam.traffic.vehicle.Vehicle;

/**
 * Utility class that encapsulates overtake manoeuvre logic with safety checks.
 * 
 * Now follows the architectural principle of separating decision-making, 
 * manoeuvre execution, and physical control.
 */
public class OvertakeStrategy {

    private OvertakeStrategy() { /* utility class – no instances */ }

    /**
     * Executes an overtake manoeuvre with full safety checks.
     * 
     * @param v the vehicle that is overtaking
     * @param targetLane the target lane for overtaking
     * @param leaderOldSpeed speed of leader in current lane (m/s) or -1 if none
     * @param gapToLeaderOld distance to leader in current lane (m)
     * @param leaderNewSpeed speed of leader in target lane (m/s) or -1 if none
     * @param gapToLeaderNew distance to leader in target lane (m)
     * @param followerNewSpeed speed of follower in target lane (m/s) or -1 if none
     * @param gapToFollowerNew distance to follower in target lane (m)
     * @return true if overtake was successfully executed
     */
    public static boolean executeOvertake(
        Vehicle v,
        Lane targetLane,
        double leaderOldSpeed,
        double gapToLeaderOld,
        double leaderNewSpeed,
        double gapToLeaderNew,
        double followerNewSpeed,
        double gapToFollowerNew
    ) {
        // 1. Safety and physical possibility checks
        if (!isOvertakePhysicallyPossible(v, targetLane)) {
            return false;
        }
        
        if (!isOvertakeSafe(
            v.getSpeed(), v.getMaxSpeed(),
            leaderOldSpeed, gapToLeaderOld,
            leaderNewSpeed, gapToLeaderNew,
            followerNewSpeed, gapToFollowerNew
        )) {
            return false;
        }

        // 2. Execute the manoeuvre
        System.out.println(v.getType() + " executing safe overtake");
        accelerateForOvertake(v);
        changeLaneForOvertake(v, targetLane.getIndex());
        
        return true;
    }

    /**
     * Checks physical possibility of lane change for overtaking.
     */
    private static boolean isOvertakePhysicallyPossible(Vehicle v, Lane targetLane) {
        Lane currentLane = v.getCurrentLane();
        if (currentLane == null) return false;

        int currentIndex = currentLane.getIndex();
        int targetIndex = targetLane.getIndex();
        
        if (targetIndex == currentIndex - 1) {
            return currentLane.canCrossLeft(v.getType().toVehicleCategory(), v.isEmergency());
        } else if (targetIndex == currentIndex + 1) {
            return currentLane.canCrossRight(v.getType().toVehicleCategory(), v.isEmergency());
        }
        
        return false;
    }

    /**
     * Safety check using MOBIL model.
     */
    private static boolean isOvertakeSafe(
        double subjectSpeed,
        double subjectMaxSpeed,
        double leaderOldSpeed,
        double gapToLeaderOld,
        double leaderNewSpeed,
        double gapToLeaderNew,
        double followerNewSpeed,
        double gapToFollowerNew
    ) {
        return LaneChange.isLaneChangeSafeMOBIL(
            subjectSpeed, subjectMaxSpeed,
            leaderOldSpeed, gapToLeaderOld,
            leaderNewSpeed, gapToLeaderNew,
            followerNewSpeed, gapToFollowerNew
        );
    }

    /**
     * Acceleration strategy for overtaking.
     */
    private static void accelerateForOvertake(Vehicle v) {
        // Use IDM acceleration model for smooth overtaking
        double acceleration = DistanceKeeping.calculateIDMAcceleration(
            v.getSpeed(), v.getMaxSpeed(),
            -1, // No leader in new position yet
            Double.MAX_VALUE // Large gap for overtaking
        );
        v.applyAcceleration(acceleration, 0.1); // Apply for 0.1s time step
    }

    /**
     * Lane change execution for overtaking.
     */
    private static void changeLaneForOvertake(Vehicle v, int targetLaneIndex) {
        v.changeLaneIndex(targetLaneIndex);
    }
}