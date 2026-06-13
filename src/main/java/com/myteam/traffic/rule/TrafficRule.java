package com.myteam.traffic.rule;

import com.myteam.traffic.behavior.common.Action;
import com.myteam.traffic.context.RoadContext;
import com.myteam.traffic.vehicle.Vehicle;

public interface TrafficRule {
    boolean isAllowed(Vehicle v, Action a, RoadContext c);

    int getPriority();

    boolean appliesTo(Vehicle v);
}
