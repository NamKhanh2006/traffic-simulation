package traffic.behavior.common;

import traffic.model.vehicle.Vehicle;

/**
 * Utility class for U-turn manoeuvres.
 *
 * A U-turn is a special-case turn that reverses the vehicle's direction
 * entirely. It is kept separate from Turn because it requires more space,
 * has different legality rules (see MarkingRule / ActionRule), and may
 * need a distinct animation from the GUI team.
 */
public class UTurn {

    private UTurn() { /* utility class – no instances */ }

    /**
     * Execute a U-turn for the specified vehicle.
     * The caller is responsible for checking that a U-turn is legally
     * allowed at the current position (via TrafficController) before
     * invoking this method.
     *
     * @param v the vehicle performing the U-turn
     */
    public static void uTurn(Vehicle v) {
        System.out.println(v.getType() + " performing U-turn");
        v.uTurn();
    }
}
