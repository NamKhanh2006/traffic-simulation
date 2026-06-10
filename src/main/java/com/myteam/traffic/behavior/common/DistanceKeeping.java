package com.myteam.traffic.behavior.common;

/**
 * Pure math utility class for safe following-distance logic with TTC and IDM.
 * Follows Strategic Design Pattern - contains ONLY calculation logic.
 */
public class DistanceKeeping {
    private DistanceKeeping() {}

    // Ngưỡng an toàn theo thời gian (giây)
    public static final double SAFE_TTC = 2.0;      // TTC an toàn
    public static final double MIN_TTC = 1.0;       // Dưới ngưỡng này phải can thiệp

    // IDM parameters
    public static final double DEFAULT_MAX_ACCEL = 2.0;      // m/s²
    public static final double DEFAULT_COMFORT_DECEL = 3.0;  // m/s²
    public static final double MIN_GAP = 2.0;                // m
    public static final double DESIRED_TIME_HEADWAY = 1.5;   // s
    
    /**
     * Tính Time-to-Collision giữa hai xe.
     * @param currentSpeed Speed of subject vehicle (m/s)
     * @param leaderSpeed Speed of front vehicle (m/s)
     * @param gap Distance between vehicles (m)
     * @return TTC (seconds), or POSITIVE_INFINITY if no collision course
     */
    public static double calculateTTC(
        double currentSpeed, 
        double leaderSpeed, 
        double gap
    ) {
        double relSpeed = currentSpeed - leaderSpeed;
        if (relSpeed <= 0) return Double.POSITIVE_INFINITY;
        return gap / relSpeed;
    }

    public static double calculateTimeHeadway(
        double currentSpeed, 
        double gap
    ) {
        if (currentSpeed <= 0) return Double.POSITIVE_INFINITY;
        return gap / currentSpeed;
    }

    /**
     * Checks if immediate collision avoidance is needed.
     * @param ttc Time-to-collision value
     * @return true if collision is imminent
     */
    public static boolean isImminentCollision(double ttc) {
        return ttc < MIN_TTC;
    }

    /**
     * Checks if following distance is unsafe.
     * @param timeHeadway Current time headway
     * @return true if below safe threshold
     */
    public static boolean isHeadwayUnsafe(double timeHeadway) {
        return timeHeadway < DESIRED_TIME_HEADWAY;
    }

    /**
     * Intelligent Driver Model - calculates desired acceleration.
     * 
     * @param currentSpeed Subject vehicle speed (m/s)
     * @param maxSpeed     Subject vehicle max speed (m/s)
     * @param leaderSpeed  Front vehicle speed (m/s) (0 if no leader)
     * @param gap          Distance to front vehicle (m)
     * @param aMax         Maximum acceleration (m/s²)
     * @param decel        Comfortable deceleration (m/s²)
     * @return Recommended acceleration (can be negative)
     */
    public static double calculateIDMAcceleration(
        double currentSpeed,
        double maxSpeed,
        double leaderSpeed,
        double gap,
        double aMax,
        double decel
    ) {
        // Free road acceleration component
        double freeComponent = 1 - Math.pow(currentSpeed / maxSpeed, 4);
        
        // Return free acceleration if no vehicle ahead
        if (leaderSpeed < 0 || gap <= 0) {
            return aMax * freeComponent;
        }

        double relSpeed = currentSpeed - leaderSpeed;
        double sStar = MIN_GAP
            + currentSpeed * DESIRED_TIME_HEADWAY
            + (currentSpeed * relSpeed) / (2 * Math.sqrt(aMax * decel));
        
        double interactionComponent = Math.pow(sStar / Math.max(0.1, gap), 2);
        
        return aMax * (freeComponent - interactionComponent);
    }

    /**
     * Simplified IDM with default parameters.
     */
    public static double calculateIDMAcceleration(
        double currentSpeed,
        double maxSpeed,
        double leaderSpeed,
        double gap
    ) {
        return calculateIDMAcceleration(
            currentSpeed, 
            maxSpeed, 
            leaderSpeed, 
            gap,
            DEFAULT_MAX_ACCEL, 
            DEFAULT_COMFORT_DECEL
        );
    }
}