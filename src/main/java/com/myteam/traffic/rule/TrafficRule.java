package com.myteam.traffic.rule;

import com.myteam.traffic.*;

public interface TrafficRule {
	boolean isAllowed(Vehicle v, Action a, RoadContext c);

	int getPriority();
}
