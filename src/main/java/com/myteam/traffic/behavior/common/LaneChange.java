package com.myteam.traffic.behavior.common;

import com.myteam.traffic.vehicle.Vehicle;

/**
 * Utility class for lane-change manoeuvres.
 *
 * The actual lane-switch logic (checking adjacent lane availability, etc.)
 * will be coordinated with the infrastructure team's Lane/RoadSegment model.
 * For now this records the event and delegates to Vehicle.
 */
public class LaneChange {

    private LaneChange() { /* utility class – no instances */ }

    /**
     * Execute a lane change for the given vehicle.
     * The vehicle itself decides the target lane; this helper ensures
     * the event is logged consistently regardless of driver type.
     *
     * @param v the vehicle that is changing lanes
     */
    public static void changeLane(Vehicle v) {
        System.out.println(v.getType() + " changing lane");
        v.changeLane();
    }
}
