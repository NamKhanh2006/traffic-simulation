package traffic.behavior.common;

import traffic.model.vehicle.Vehicle;

/**
 * Utility class for turning manoeuvres (left or right turn).
 *
 * Used at intersections when a driver behaviour decides the vehicle
 * must turn. The direction is expressed as an enum to make call sites
 * readable and to allow the GUI team to animate the correct arc.
 */
public class Turn {

    /** The direction in which to turn. */
    public enum Direction {
        LEFT, RIGHT
    }

    private Turn() { /* utility class – no instances */ }

    /**
     * Execute a turn in the given direction for the specified vehicle.
     *
     * @param v         the vehicle turning
     * @param direction LEFT or RIGHT
     */
    public static void turn(Vehicle v, Direction direction) {
        System.out.println(v.getType() + " turning " + direction.name().toLowerCase());
        v.turn(direction);
    }
}
