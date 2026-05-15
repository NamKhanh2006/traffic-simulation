package com.myteam.traffic.rule;

/*

public class SignalRule implements TrafficRule {
    public boolean isAllowed(Vehicle v, Action a, RoadContext c) {
        if (c.isRedLight() && a == Action.MOVE_FORWARD)
        	return false;
        if (c.mustStop() && a != Action.STOP)
        	return false;
        if (c.mustGiveWay(v) && !c.canProceedSafely(v))
        	return false;
        return true;
    }
}

*/