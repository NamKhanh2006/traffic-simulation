package com.myteam.traffic.behavior;

import com.myteam.traffic.common.Action;
import com.myteam.traffic.model.context.RoadContext;
import com.myteam.traffic.model.vehicle.Vehicle;

public class EmergencyDriver implements DriverBehavior {

    @Override
    public Action decideAction(Vehicle v, RoadContext c) {

        if (!v.isSirenOn()) return Action.HONK;

        if (c.getFrontVehicle() != null) return Action.OVERTAKE;

        return Action.MOVE_FORWARD;
    }
}
