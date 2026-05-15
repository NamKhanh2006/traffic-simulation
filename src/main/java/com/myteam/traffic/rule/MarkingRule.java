package com.myteam.traffic.rule;

import java.util.*;
import com.myteam.traffic.marking.*;
import com.myteam.traffic.common.*;
import com.myteam.traffic.context.RoadContext;
import com.myteam.traffic.vehicle.Vehicle;

/*
public class MarkingRule implements TrafficRule {

    @Override
    public boolean isAllowed(Vehicle v, Action a, RoadContext c) {

        for (RoadMarking m : c.getMarkings()) {

            if (m.isVehicleCrossing(v)) {

                if (!isAllowedByType(m, v, a, c)) {
                    return false;
                }
            }
        }

        return true;
    }

    private boolean isAllowedByType(RoadMarking m, Vehicle v, Action a, RoadContext c) {

        switch (m.getType()) {

            case LANE_DIVIDER:
            case CENTER_LINE:
                return a != Action.CHANGE_LANE && a != Action.OVERTAKE;

            case STOP_LINE:
                return !(c.isRedLight() && a == Action.MOVE_FORWARD);

            case DIRECTION_ARROW:
                return checkDirection(a);

            default:
                return true;
        }
    }

    private boolean checkDirection(Action a) {
        // TODO: refine
        return true;
    }
}*/
// The commented code above will be abandoned

public class MarkingRule implements TrafficRule{
	private HashSet<Vehicle> affectedVehicles; // null = ALL VEHICLES AFFECTED
	
	@Override
	public boolean isAllowed(Vehicle v, Action a, RoadContext c) {
		if (affectedVehicles != null && !affectedVehicles.contains(v))
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
		
	}
}