package com.myteam.traffic.behavior.common;

import com.myteam.traffic.vehicle.Vehicle;

/**
 * Utility class that encapsulates overtake manoeuvre logic.
 *
 * An overtake involves accelerating and moving to a faster/clearer lane.
 * This helper ensures consistent logging and delegates movement to Vehicle.
 */
public class OvertakeStrategy {

    private OvertakeStrategy() { /* utility class – no instances */ }

    /**
     * Instruct the vehicle to execute an overtake manoeuvre.
     * The vehicle accelerates and requests a lane change simultaneously.
     *
     * @param v the vehicle that is overtaking
     */
    public static void overtake(Vehicle v) {
        System.out.println(v.getType() + " overtaking");
        v.accelerate();
        v.changeLane();
    }
}
