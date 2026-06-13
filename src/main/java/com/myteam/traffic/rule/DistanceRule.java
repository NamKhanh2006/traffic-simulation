package com.myteam.traffic.rule;

import java.util.HashSet;

import com.myteam.traffic.behavior.common.Action;
import com.myteam.traffic.context.RoadContext;
import com.myteam.traffic.vehicle.Vehicle;
import com.myteam.traffic.vehicle.VehicleType;

public class DistanceRule implements TrafficRule {
    private double minDistance;
    private HashSet<VehicleType> affectedVehicles;

    public DistanceRule(double minDistance, HashSet<VehicleType> affectedVehicles) {
        this.minDistance = minDistance;
        this.affectedVehicles = affectedVehicles;
    }

    @Override
    public boolean isAllowed(Vehicle v, Action a, RoadContext c) {
        return c.distanceAfterAction(v, a) >= minDistance;
    }

    @Override
    public int getPriority() {
        return 90;
    }

    @Override
    public boolean appliesTo(Vehicle v) {
        if (affectedVehicles == null) {
            return true;
        }
        return affectedVehicles.contains(v.getType());
    }
}
