package traffic.behavior;

import traffic.common.Action;
import traffic.model.context.RoadContext;
import traffic.model.vehicle.Vehicle;

/**
 * The "brain" of every vehicle.
 * Each concrete driver type implements this to decide what the vehicle does next.
 *
 * The loop each tick:
 *   1. decideAction()  → propose an action
 *   2. controller.isVehicleAllowed() → validate
 *   3. if rejected → handleRejection() → pick a safe fallback
 */
public interface DriverBehavior {

    /**
     * Examine the current vehicle state and surrounding road context,
     * then return the next action the vehicle wants to attempt.
     */
    Action decideAction(Vehicle v, RoadContext context);

    /**
     * Called by the controller when the proposed action was rejected.
     * Each driver type must return a safe fallback action.
     *
     * @param v        the vehicle whose action was rejected
     * @param context  the current road context
     * @param rejected the action that was not allowed
     * @return a fallback Action that is more likely to be permitted
     */
    Action handleRejection(Vehicle v, RoadContext context, Action rejected);

    /**
     * Whether this driver is an emergency vehicle and can bypass
     * certain traffic rules (e.g. red lights, speed limits).
     * Default: false. Override in EmergencyDriver.
     */
    default boolean isEmergency() {
        return false;
    }

    /** Mức độ hung hăng (0 = an toàn, 1 = rất hung hăng) */
    default double getAggressiveness() { return 0.0; }

    /** Hệ số lịch sự khi đổi làn (MOBIL), càng cao càng quan tâm xe sau */
    default double getPolitenessFactor() { return 0.5; }

    /** Thời gian an toàn mong muốn (giây) – dùng trong IDM */
    default double getDesiredTimeHeadway() { return 1.5; }
}
