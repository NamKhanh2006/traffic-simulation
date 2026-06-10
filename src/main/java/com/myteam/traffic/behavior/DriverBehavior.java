package com.myteam.traffic.behavior;

import com.myteam.traffic.common.Action;
import com.myteam.traffic.model.context.RoadContext;
import com.myteam.traffic.model.vehicle.Vehicle;

public interface DriverBehavior {
    Action decideAction(Vehicle v, RoadContext context);
}
