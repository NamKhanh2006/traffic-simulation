package traffic.behavior;

import traffic.common.Action;
import traffic.model.context.RoadContext;
import traffic.model.vehicle.Vehicle;

/**
 * Driver behaviour for emergency vehicles (ambulance, fire truck, police).
 *
 * Decision priority (highest → lowest):
 *   1. Siren not on yet → HONK  (activate siren first)
 *   2. Vehicle ahead    → OVERTAKE
 *   3. Default          → MOVE_FORWARD  (high-speed transit)
 *
 * Emergency vehicles bypass most traffic rules — the TrafficController
 * checks isEmergency() and skips rules whose affectedVehicles list does
 * not include emergency vehicles.
 *
 * Rejection fallback:
 *   Emergency vehicles are rarely rejected, but if they are (e.g. physical
 *   impossibility) they fall back to SLOW_DOWN rather than STOP, to keep
 *   moving as much as possible.
 */
public class EmergencyDriver implements DriverBehavior {

    @Override
    public Action decideAction(Vehicle v, RoadContext context) {

        // 1. Siren must be active before clearing the road
        if (!v.isSirenOn()) {
            return Action.HONK;
        }

        // 2. Clear any vehicle blocking the path
        if (context.hasFrontVehicle()) {
            return Action.OVERTAKE;
        }

        // 3. Path is clear – proceed at full speed
        return Action.MOVE_FORWARD;
    }

    @Override
    public Action handleRejection(Vehicle v, RoadContext context, Action rejected) {
        // Emergency vehicles keep moving even when rejected – slow down rather than stop
        return Action.SLOW_DOWN;
    }

    /**
     * Signals the TrafficController to skip non-applicable rules.
     * Rules that do not include emergency vehicles in their affectedVehicles
     * list will be ignored automatically.
     */
    @Override
    public boolean isEmergency() {
        return true;
    }
}
