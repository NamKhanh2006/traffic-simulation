package com.myteam.traffic.controller;

import java.util.List;
import com.myteam.traffic.context.RoadContext;
import com.myteam.traffic.common.Action;
import com.myteam.traffic.rule.TrafficRule;
// remember to import Vehicle class when start coding the methods

public class TrafficController {
    List<TrafficRule> rules;

    public boolean isVehicleAllowed(Vehicle vehicle, Action action, RoadContext context) {
        // check the rules based on the action and vehicle type
        // return true if allowed, false otherwise
        return false; // placeholder return value
    }
}
