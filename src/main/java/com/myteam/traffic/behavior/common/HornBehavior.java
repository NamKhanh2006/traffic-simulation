package com.myteam.traffic.behavior.common;

import traffic.common.Action;
import traffic.model.context.RoadContext;
import traffic.model.vehicle.Vehicle;

public class HornBehavior {
    public static Action decide(Vehicle v, RoadContext c) {
        if (c.getFrontVehicle() != null && Math.random() < 0.2) {
            return Action.HONK;
        }
        return null;
    }
}
