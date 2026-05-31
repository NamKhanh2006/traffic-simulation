package com.myteam.traffic.behavior;

import traffic.common.Action;
import traffic.model.context.RoadContext;
import traffic.model.vehicle.Vehicle;

public class NormalDriver implements DriverBehavior {

    @Override
    public Action decideAction(Vehicle v, RoadContext c) {

        if (c.getFrontVehicle() != null) {
            double dist = c.getFrontVehicle().getX() - v.getX();
            if (dist < 5) return Action.SLOW_DOWN;
        }

        if (c.hasRedLightAhead()) return Action.STOP;

        if (c.hasEmergencyNearby()) return Action.CHANGE_LANE;

        return Action.MOVE_FORWARD;
    }
}
