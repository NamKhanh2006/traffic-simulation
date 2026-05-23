package traffic.behavior.common;

import traffic.model.vehicle.Vehicle;

/**
 * Utility class encapsulating honking logic.
 *
 * Separating horn behaviour from the driver decision allows different
 * driver types (Normal, Aggressive, Emergency) to reuse the same honk
 * call while keeping their decision trees clean.
 */
public class HornBehavior {

    private HornBehavior() { /* utility class – no instances */ }

    /**
     * Instruct the vehicle to sound its horn and log the event.
     *
     * @param v the vehicle that honks
     */
    public static void honk(Vehicle v) {
        v.honk();
        System.out.println(v.getType() + " honked!");
    }
}
