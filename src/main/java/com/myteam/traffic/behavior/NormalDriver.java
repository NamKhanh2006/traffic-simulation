package traffic.behavior;

import traffic.common.Action;
import traffic.model.context.RoadContext;
import traffic.model.vehicle.Vehicle;

/**
 * A law-abiding, safety-conscious driver.
 *
 * Decision priority (highest → lowest):
 *   1. Red light ahead      → STOP
 *   2. Too close to front   → SLOW_DOWN
 *   3. Emergency vehicle nearby → CHANGE_LANE
 *   4. Default              → MOVE_FORWARD
 */
public class NormalDriver implements DriverBehavior {

    @Override
    public javax.swing.Action decideAction(Vehicle v, RoadContext context) {

        // 1. Obey red light
        if (context.hasRedLightAhead()) {
            return Action.STOP;
        }

        // 2. Keep a safe following distance
        if (context.isTooCloseToFront()) {
            return Action.SLOW_DOWN;
        }

        // 3. Yield to emergency vehicles
        if (context.hasEmergencyNearby()) {
            return Action.CHANGE_LANE;
        }

        // 4. All clear – move forward
        return Action.MOVE_FORWARD;
    }
}
