package com.myteam.traffic.behavior.common;

import traffic.common.Action;
import traffic.model.context.RoadContext;
import traffic.model.vehicle.Vehicle;

public class OvertakeDecision {

    public static Action decide(Vehicle v, RoadContext c) {
        if (c.getFrontVehicle() == null) return null;

        double dist = c.getFrontVehicle().getX() - v.getX();

        if (dist < 8 && c.canChangeLane()) {
            return Action.OVERTAKE;
        }

        return null;
    }
}
