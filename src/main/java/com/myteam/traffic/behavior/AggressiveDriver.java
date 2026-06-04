package traffic.behavior;

import java.util.Random;

import com.myteam.traffic.behavior.common.Action;
import com.myteam.traffic.context.RoadContext;
import com.myteam.traffic.vehicle.Vehicle;

/**
 * An impatient, speed-focused driver who frequently overtakes and honks.
 *
 * Decision priority (highest → lowest):
 *   1. Below max speed → ACCELERATE
 *   2. Vehicle ahead   → OVERTAKE
 *   3. Random chance   → HONK  (impulsive)
 *   4. Default         → MOVE_FORWARD
 *
 * Rejection fallback:
 *   - If OVERTAKE rejected (e.g. solid line marking) → HONK (frustrated)
 *     then SLOW_DOWN on the next tick
 *   - If ACCELERATE rejected (e.g. speed limit)      → MOVE_FORWARD
 *   - Any other rejection                            → SLOW_DOWN
 */
public class AggressiveDriver implements DriverBehavior {

    private static final double HONK_PROBABILITY = 0.15;

    private final Random random = new Random();

    @Override
    public Action decideAction(Vehicle v, RoadContext context) {

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

    @Override
    public Action handleRejection(Vehicle v, RoadContext context, Action rejected) {
        switch (rejected) {
            case OVERTAKE:
                // Frustrated – honk, then next tick will slow down
                return Action.HONK;
            case ACCELERATE:
                // Hit speed limit – just cruise
                return Action.MOVE_FORWARD;
            default:
                return Action.SLOW_DOWN;
        }
    }

    // isEmergency() returns false by default – no override needed
}
