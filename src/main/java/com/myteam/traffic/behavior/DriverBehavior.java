package com.myteam.traffic.behavior;

import traffic.common.Action;
import traffic.model.context.RoadContext;
import traffic.model.vehicle.Vehicle;

public interface DriverBehavior {
    Action decideAction(Vehicle v, RoadContext context);
}
