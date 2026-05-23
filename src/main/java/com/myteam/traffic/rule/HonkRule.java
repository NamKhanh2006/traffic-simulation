package com.myteam.traffic.rule;

import com.myteam.traffic.*;
import java.util.HashSet;
import com.myteam.traffic.vehicle.*;
import com.myteam.traffic.context.*;
import com.myteam.traffic.behavior.*;
import com.myteam.traffic.behavior.common.*;
import com.myteam.traffic.vehicle.emergency.*;

public class HonkRule implements TrafficRule {
    private boolean mustHorn; // true = bắt buộc, false = cấm
    private HashSet<VehicleType> affectedVehicles; // null = ALL VEHICLES AFFECTED
    
    @Override
    public boolean isAllowed(Vehicle v, Action a, RoadContext c) {
    	if (affectedVehicles != null && !affectedVehicles.contains(v.getType()))
    		return true;
        if (a != Action.HONK)
        	return true;
        return mustHorn; // nếu mustHorn=false → cấm
    }
    
    @Override
    public int getPriority() {
    	return 5;
    }
}
