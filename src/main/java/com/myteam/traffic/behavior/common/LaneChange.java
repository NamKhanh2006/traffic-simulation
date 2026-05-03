package com.myteam.traffic.behavior.common;

import traffic.common.Action;
import traffic.model.context.RoadContext;
import traffic.model.vehicle.Vehicle;

public class LaneChange {
    public static Action decide(Vehicle v, RoadContext c) {
        if (c.canChangeLane() && c.isLaneBlocked()) {
            return Action.CHANGE_LANE;
        }
        return null;
    }
}
