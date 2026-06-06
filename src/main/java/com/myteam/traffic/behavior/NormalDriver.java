package com.myteam.traffic.behavior;

import com.myteam.traffic.behavior.common.Action;
import com.myteam.traffic.context.RoadContext;
import com.myteam.traffic.vehicle.Vehicle;

/**
 * A law-abiding, safety-conscious driver.
 *
 * Decision priority (highest → lowest):
 *   1. Red light ahead          → STOP
 *   2. Too close to front       → SLOW_DOWN
 *   3. Emergency vehicle nearby → CHANGE_LANE
 *   4. Default                  → MOVE_FORWARD
 *
 * Rejection fallback:
 *   - If OVERTAKE rejected  → SLOW_DOWN (safe choice)
 *   - If CHANGE_LANE rejected → SLOW_DOWN
 *   - Any other rejection   → STOP (safest default)
 */
public class NormalDriver implements DriverBehavior {

    @Override
    public Action decideAction(Vehicle v, RoadContext context) {

        // 1. Obey red light (không liên quan vòng xuyến — đèn và nhập vòng độc lập)
        if (context.hasRedLightAhead()) {
            return Action.STOP;
        }

        // 2. Nhường xe đã trên cung khi sắp merge
        if (context.mustYieldToIntersectionTraffic()) {
            return Action.STOP;
        }

        // 3. Giữ khoảng cách (segment hoặc quỹ đạo giao lộ)
        if (context.isTooCloseToFront()) {
            return Action.SLOW_DOWN;
        }

        // 4. Yield to emergency vehicles
        if (context.hasEmergencyNearby()) {
            return Action.CHANGE_LANE;
        }

        // 5. All clear – move forward
        return Action.MOVE_FORWARD;
    }

    @Override
    public Action handleRejection(Vehicle v, RoadContext context, Action rejected) {
        // Normal driver always falls back to something safe
        switch (rejected) {
            case OVERTAKE:
            case CHANGE_LANE:
                return Action.SLOW_DOWN;
            default:
                return Action.STOP;
        }
    }

    // isEmergency() returns false by default – no override needed
}
