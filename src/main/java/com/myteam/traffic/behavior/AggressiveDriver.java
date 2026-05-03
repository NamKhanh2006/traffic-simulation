package com.myteam.traffic.behavior;

import traffic.common.Action;
import traffic.model.context.RoadContext;
import traffic.model.vehicle.Vehicle;

public class AggressiveDriver implements DriverBehavior {

    @Override
    public Action decideAction(Vehicle v, RoadContext c) {

        if (v.getSpeed() < v.getMaxSpeed()) return Action.ACCELERATE;

        if (c.getFrontVehicle() != null) return Action.OVERTAKE;

        if (Math.random() < 0.3) return Action.HONK;

        return Action.MOVE_FORWARD;
    }
}
