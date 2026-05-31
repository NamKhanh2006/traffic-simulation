package com.myteam.traffic.behavior.common;

import traffic.common.Action;
import traffic.model.context.RoadContext;
import traffic.model.vehicle.Vehicle;

public class Turn {
    public static Action decide(Vehicle v, RoadContext c) {
        if (c.isAtIntersection() && c.hasTurnSignal()) {
            return Action.TURN;
        }
        return null;
    }
}
