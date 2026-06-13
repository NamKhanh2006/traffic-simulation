package com.myteam.traffic.behavior.common;

import com.myteam.traffic.model.infrastructure.Lane;
import com.myteam.traffic.vehicle.PlannedExit;
import com.myteam.traffic.vehicle.Vehicle;
import com.myteam.traffic.vehicle.TravelMode;


/**
 * Utility class for turning manoeuvres (left/right/U-turn).
 * Performs validation before setting planned exit.
 */
public final class Turn {
    
    private Turn() { /* utility class - no instances */ }

    /**
     * Validates and executes turn manoeuvre.
     * 
     * @param vehicle      Vehicle attempting to turn
     * @param plannedExit  Requested exit direction
     * @return true if turn is valid and executed, false otherwise
     */
    public static boolean executeTurn(Vehicle vehicle, PlannedExit plannedExit) {
        // 0. Validate planned Exit
        if (plannedExit == null || plannedExit == PlannedExit.NONE) {
            return false;
        }
        // 1. Validate vehicle state
        if (vehicle.getTravelMode() != TravelMode.ON_SEGMENT) {
            return false; // Only allow turning when on segment
        }
        
        // 2. Validate lane permissions
        Lane currentLane = vehicle.getCurrentLane();
        if (currentLane == null) {
            return false;
        }
        
        Lane.Movement requiredMovement = mapToMovement(plannedExit);
        if (!currentLane.allowsMovement(
            requiredMovement, 
            vehicle.getType().toVehicleCategory(), 
            vehicle.isEmergency())
        ) {
            return false;
        }
        
        // 3. Execute physical change
        vehicle.setPlannedExit(plannedExit);
        return true;
    }

    private static Lane.Movement mapToMovement(PlannedExit exit) {
        return switch (exit) {
            case LEFT -> Lane.Movement.LEFT;
            case RIGHT -> Lane.Movement.RIGHT;
            case STRAIGHT -> Lane.Movement.STRAIGHT;
            case U_TURN -> Lane.Movement.U_TURN;
            case RANDOM -> Lane.Movement.STRAIGHT; // Default mapping
            default -> null;
        };
    }
}