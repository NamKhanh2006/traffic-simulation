package com.myteam.traffic.behavior.common;

import traffic.common.Action;
import traffic.model.context.RoadContext;
import traffic.model.vehicle.Vehicle;


public class DistanceKeeping {
    public static Action decide(Vehicle v, RoadContext c) {
        if (c.getFrontVehicle() == null) return null;

        double dist = c.getFrontVehicle().getX() - v.getX();

        if (dist < 5) return Action.SLOW_DOWN;
        if (dist > 10) return Action.ACCELERATE;

        return null;
    }
}
