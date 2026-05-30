package com.myteam.traffic.rule;

import java.util.*;
import com.myteam.traffic.behavior.*;
import com.myteam.traffic.behavior.common.*;
import com.myteam.traffic.marking.*;
import com.myteam.traffic.common.*;
import com.myteam.traffic.context.RoadContext;
import com.myteam.traffic.vehicle.Vehicle;
import com.myteam.traffic.vehicle.*;

public class MarkingRule implements TrafficRule{
	private HashSet<VehicleType> affectedVehicles; // null = ALL VEHICLES AFFECTED
	
	@Override
	public boolean isAllowed(Vehicle v, Action a, RoadContext c) {
		if (affectedVehicles != null && !affectedVehicles.contains(v.getType()))
			return true;
		List<RoadMarking> markings = c.getMarkings();
		
		for (RoadMarking m : markings) {
			if (v.isCrossingAfterAction(a, m) && !m.getIsCrossingAllowed())
				return false;
		}
		
		return true;
		
	}
	
	@Override
	public int getPriority() {
		return 40;
	}
}