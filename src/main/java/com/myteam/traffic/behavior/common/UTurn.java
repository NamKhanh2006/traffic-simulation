package com.myteam.traffic.behavior.common;

import traffic.common.Action;
import traffic.model.context.RoadContext;
import traffic.model.vehicle.Vehicle;

public class UTurn {
    public static Action decide(Vehicle v, RoadContext c) {
        if (c.isUTurnAllowed()) {
            return Action.UTURN;
        }
        return null;
    }
}
