package traffic.behavior.common;

import traffic.common.Action;
import traffic.model.vehicle.Vehicle;

/**
 * Utility class for safe following-distance logic.
 *
 * Used by driver behaviours (e.g. NormalDriver) before deciding an action.
 * The threshold is expressed in the same distance unit as Vehicle.getX().
 */
public class DistanceKeeping {

    /** Minimum safe gap (in distance units) between two vehicles. */
    public static final double SAFE_DISTANCE = 5.0;

    private DistanceKeeping() { /* utility class – no instances */ }

    /**
     * Check whether {@code self} is dangerously close to {@code front}.
     * If so, apply slow-down and log the event.
     *
     * @param self  the vehicle that is following
     * @param front the vehicle directly ahead (may be null)
     * @return the corrective Action if the gap is too small, null otherwise
     */
    public static Action keepDistance(Vehicle self, Vehicle front) {
        if (front == null) return null;

        double gap = front.getX() - self.getX();
        if (gap < SAFE_DISTANCE) {
            self.slowDown();
            System.out.println(self.getType() + " slowing down to keep safe distance (gap=" + gap + ")");
            return Action.SLOW_DOWN;
        }
        return null;
    }
}
