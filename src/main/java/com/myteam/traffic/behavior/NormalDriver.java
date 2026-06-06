// BTNode.java
package com.myteam.traffic.behavior.bt;

import com.myteam.traffic.behavior.common.Action;
import com.myteam.traffic.context.RoadContext;
import com.myteam.traffic.vehicle.Vehicle;

@FunctionalInterface
public interface BTNode {
    Action evaluate(Vehicle v, RoadContext context);
}

// SelectorNode.java
public class SelectorNode implements BTNode {
    private final BTNode[] children;
    public SelectorNode(BTNode... children) { this.children = children; }
    @Override
    public Action evaluate(Vehicle v, RoadContext context) {
        for (BTNode child : children) {
            Action result = child.evaluate(v, context);
            if (result != null) return result;
        }
        return null;
    }
}

// SequenceNode.java
public class SequenceNode implements BTNode {
    private final BTNode[] children;
    public SequenceNode(BTNode... children) { this.children = children; }
    @Override
    public Action evaluate(Vehicle v, RoadContext context) {
        for (BTNode child : children) {
            Action result = child.evaluate(v, context);
            if (result == null) return null;
        }
        return Action.MOVE_FORWARD;
    }
}

// Leaf nodes
public class HasRedLightAhead implements BTNode {
    @Override
    public Action evaluate(Vehicle v, RoadContext ctx) {
        return ctx.hasRedLightAhead() ? Action.STOP : null;
    }
}

public class IsTooCloseToFront implements BTNode {
    @Override
    public Action evaluate(Vehicle v, RoadContext ctx) {
        // Dùng TTC thay vì khoảng cách
        return ctx.isCollisionImminent() ? Action.SLOW_DOWN : null;
    }
}

public class HasEmergencyNearby implements BTNode {
    @Override
    public Action evaluate(Vehicle v, RoadContext ctx) {
        return ctx.hasEmergencyNearby() ? Action.CHANGE_LANE : null;
    }
}
