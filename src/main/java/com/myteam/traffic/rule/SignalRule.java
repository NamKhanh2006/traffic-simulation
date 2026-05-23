package com.myteam.traffic.rule;

import java.util.HashSet;
import com.myteam.traffic.behavior.*;
import com.myteam.traffic.behavior.common.*;
import com.myteam.traffic.vehicle.*;
import com.myteam.traffic.vehicle.emergency.*;
import com.myteam.traffic.context.*;

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
    
    public int getPriority() {
    	return 45;
    }
}

