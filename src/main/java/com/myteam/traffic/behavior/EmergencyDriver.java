package traffic.behavior;

import traffic.common.Action;
import traffic.model.context.RoadContext;
import traffic.model.vehicle.Vehicle;

/**
 * Driver behaviour for emergency vehicles (ambulance, fire truck, police).
 *
 * Decision priority (highest → lowest):
 *   1. Siren not on yet    → HONK  (activate siren first)
 *   2. Vehicle ahead       → OVERTAKE
 *   3. Default             → MOVE_FORWARD  (high-speed transit)
 *
 * Emergency vehicles may legally ignore red lights and speed limits.
 */
public class EmergencyDriver implements DriverBehavior {

    @Override
    public javax.swing.Action decideAction(Vehicle v, RoadContext context) {

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
}
