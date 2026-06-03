package com.myteam.traffic.rule;

import java.util.HashSet;
import com.myteam.traffic.*;
import com.myteam.traffic.vehicle.*;
import com.myteam.traffic.context.*;
import com.myteam.traffic.behavior.*;
import com.myteam.traffic.behavior.common.*;
import com.myteam.traffic.vehicle.emergency.*;

public class DistanceRule implements TrafficRule {
    private double minDistance;
    private HashSet<VehicleType> affectedVehicles;  // null = ALL VEHICLES AFFECTED
    
    public DistanceRule(double minDistance, HashSet<VehicleType> affectedVehicles) {
		super();
		this.minDistance = minDistance;
		this.affectedVehicles = affectedVehicles;
	}

	@Override
    public boolean isAllowed(Vehicle v, Action a, RoadContext c) {
    	if (affectedVehicles != null && !affectedVehicles.contains(v.getType()))
        	return true;
        return c.DistanceAfterAction(v, a) >= minDistance;
    }
    
    @Override
    public int getPriority() {
    	return 50;
    }
    
    @Override
    public boolean appliesTo(Vehicle v) {
    	return true; // Any vehicle must keep a distance with the vehicle in front of it
    }
}
