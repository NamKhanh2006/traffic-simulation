package com.myteam.traffic.rule;

import java.util.HashSet;
import com.myteam.traffic.behavior.*;
import com.myteam.traffic.behavior.common.*;
import com.myteam.traffic.vehicle.*;
import com.myteam.traffic.vehicle.emergency.*;
import com.myteam.traffic.context.*;

public class VehicleFilterRule implements TrafficRule {
    private HashSet<VehicleType> allowed; // hoặc dùng banned tùy bạn
    
    @Override
    public boolean isAllowed(Vehicle v, Action a, RoadContext c) {
        return allowed == null || allowed.contains(v.getType());
    }
    
    @Override
    public int getPriority() {
    	return 50;
    }
}