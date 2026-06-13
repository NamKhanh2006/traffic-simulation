package com.myteam.traffic.behavior.common;

import com.myteam.traffic.vehicle.Vehicle;
import com.myteam.traffic.vehicle.PlannedExit;

/**
 * Utility class for U-turn manoeuvres.
 *
 * Revised architecture:
 * 1. This class handles the EXECUTION of U-turns only
 * 2. Decision logic should be handled in DriverBehavior
 * 3. Physics updates are delegated to Vehicle's atomic API
 */
public class UTurn {

    private UTurn() { /* utility class - no instances */ }

    /**
     * Execute a U-turn for the specified vehicle.
     * 
     * Revised approach:
     * 1. Sets planned exit to U_TURN (navigation instruction)
     * 2. Actual path following is handled by navigation system
     * 3. Physics updates happen through Vehicle's atomic methods
     *
     * @param v the vehicle performing the U-turn
     */
    public static void execute(Vehicle v) {
        // 1. Set navigation intent
        v.setPlannedExit(PlannedExit.U_TURN);
        
        // 2. Apply physics (gradual direction reversal)
        double currentSpeed = v.getSpeed();
        double reversalRate = calculateReversalRate(v);
        
        // 3. Update vehicle state
        v.setDirection(v.getDirection().opposite());
        v.setSpeed(currentSpeed * 0.8); // Reduce speed during maneuver
        
        System.out.println(v.getType() + " initiating U-turn at reversal rate: " + reversalRate);
    }

    /**
     * Calculates safe U-turn parameters based on vehicle physics
     */
    private static double calculateReversalRate(Vehicle v) {
        // Simplified calculation - real impl would use:
        // 1. Vehicle turning radius
        // 2. Current speed
        // 3. Road friction coefficients
        return Math.min(0.05, 1.0 / (v.getSpeed() + 0.1));
    }
}