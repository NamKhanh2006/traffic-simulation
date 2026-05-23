package traffic.behavior;

import traffic.common;
import traffic.model.context.RoadContext;
import traffic.model.vehicle.Vehicle;

import javax.swing.*;

/**
 * The "brain" of every vehicle.
 * Each concrete driver type implements this to decide what the vehicle does next.
 */
public interface DriverBehavior {
    /**
     * Examine the current vehicle state and surrounding road context,
     * then return the next action the vehicle should attempt.
     *
     * @param v       the vehicle making the decision
     * @param context the traffic situation around the vehicle
     * @return the desired Action (e.g. MOVE_FORWARD, STOP, OVERTAKE…)
     */
    Action decideAction(Vehicle v, RoadContext context);
}
