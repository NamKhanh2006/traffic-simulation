// BTNode.java
package com.myteam.traffic.behavior.bt;

import com.myteam.traffic.behavior.common.Action;
import com.myteam.traffic.context.RoadContext;
import com.myteam.traffic.vehicle.Vehicle;

@FunctionalInterface
public interface BTNode {
    Action evaluate(Vehicle v, RoadContext context);
}
