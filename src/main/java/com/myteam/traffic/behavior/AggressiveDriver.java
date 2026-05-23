package traffic.behavior;

import traffic.common.Action;
import traffic.model.context.RoadContext;
import traffic.model.vehicle.Vehicle;

import java.util.Random;

/**
 * An impatient, speed-focused driver who frequently overtakes and honks.
 *
 * Decision priority (highest → lowest):
 *   1. Below max speed     → ACCELERATE
 *   2. Vehicle ahead       → OVERTAKE
 *   3. Random chance       → HONK  (impulsive honking)
 *   4. Default             → MOVE_FORWARD
 */
public class AggressiveDriver implements traffic.behavior.DriverBehavior {

    /** Probability of honking unprompted on any given tick. */
    private static final double HONK_PROBABILITY = 0.15;

    private final Random random = new Random();

    @Override
    public javax.swing.Action decideAction(Vehicle v, RoadContext context) {

        // 1. Always try to reach max speed
        if (v.getSpeed() < v.getMaxSpeed()) {
            return Action.ACCELERATE;
        }

        // 2. Don't stay behind slower vehicles
        if (context.hasFrontVehicle()) {
            return Action.OVERTAKE;
        }

        // 3. Impulsive honk
        if (random.nextDouble() < HONK_PROBABILITY) {
            return Action.HONK;
        }

        // 4. Cruising at max speed with no obstacle
        return Action.MOVE_FORWARD;
    }
}
