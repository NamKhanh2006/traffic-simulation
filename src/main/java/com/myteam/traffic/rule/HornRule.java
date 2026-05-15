package com.myteam.traffic.rule;

import com.myteam.traffic.*;

public class HornRule implements TrafficRule {
    private boolean mustHorn; // true = bắt buộc, false = cấm
    private HashSet<VehicleType> affectedVehicles; // null = ALL VEHICLES AFFECTED
    
    @Override
    public boolean isAllowed(Vehicle v, Action a, RoadContext c) {
    	if (affectedVehicles != null && !affectedVehicles.contains(v.getType()))
    		return false;
        if (a != Action.HORN)
        	return true;
        return mustHorn; // nếu mustHorn=false → cấm
    }
    
    @Override
    public int getPriority() {
    	
    }
}
