package com.myteam.traffic.behavior.bt;

import com.myteam.traffic.behavior.common.Action;
import com.myteam.traffic.context.RoadContext;
import com.myteam.traffic.vehicle.Vehicle;

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