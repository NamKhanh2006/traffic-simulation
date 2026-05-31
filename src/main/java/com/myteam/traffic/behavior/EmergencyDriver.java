package com.myteam.traffic.behavior;

import traffic.common.Action;
import traffic.model.context.RoadContext;
import traffic.model.vehicle.Vehicle;

public class EmergencyDriver implements DriverBehavior {

    @Override
    public Action decideAction(Vehicle v, RoadContext c) {

        if (!v.isSirenOn()) return Action.HONK;

        if (c.getFrontVehicle() != null) return Action.OVERTAKE;

        return Action.MOVE_FORWARD;
    }
}
