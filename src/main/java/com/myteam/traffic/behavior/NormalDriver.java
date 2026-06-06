// NormalDriver.java
package com.myteam.traffic.behavior;

import com.myteam.traffic.behavior.common.Action;
import com.myteam.traffic.behavior.bt.*;
import com.myteam.traffic.context.RoadContext;
import com.myteam.traffic.vehicle.Vehicle;

public class NormalDriver implements DriverBehavior {
    private final BTNode decisionTree;

    public NormalDriver() {
        this.decisionTree = new SelectorNode(
            new HasRedLightAhead(),
            new IsTooCloseToFront(),
            new HasEmergencyNearby(),
            (v, ctx) -> Action.MOVE_FORWARD
        );
    }

    @Override
    public Action decideAction(Vehicle v, RoadContext context) {
        return decisionTree.evaluate(v, context);
    }

    @Override
    public Action handleRejection(Vehicle v, RoadContext context, Action rejected) {
        switch (rejected) {
            case OVERTAKE: case CHANGE_LANE: return Action.SLOW_DOWN;
            default: return Action.STOP;
        }
    }
}
