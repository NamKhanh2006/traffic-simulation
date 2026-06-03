package com.myteam.traffic.rule;

import com.myteam.traffic.*;
import com.myteam.traffic.behavior.*;
import com.myteam.traffic.behavior.common.*;
import com.myteam.traffic.context.*;
import com.myteam.traffic.vehicle.*;
import com.myteam.traffic.vehicle.emergency.*;

public interface TrafficRule {
	boolean isAllowed(Vehicle v, Action a, RoadContext c);

	int getPriority();
	
	boolean appliesTo(Vehicle v);
}
