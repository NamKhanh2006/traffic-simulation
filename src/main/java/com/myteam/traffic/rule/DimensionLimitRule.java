package com.myteam.traffic.rule;

import java.util.HashSet;
import com.myteam.traffic.*;


public class DimensionLimitRule implements TrafficRule {
    private Double maxWeight, maxHeight, maxWidth, maxLength;
    private HashSet<VehicleType> affectedVehicles; // null = tất cả

    @Override
    public boolean isAllowed(Vehicle v, Action a, RoadContext c) {
        if (affectedVehicles != null && !affectedVehicles.contains(v.getType()))
        	return true;

        if (maxWeight != null && v.getWeight() > maxWeight)
        	return false;
        if (maxHeight != null && v.getHeight() > maxHeight)
        	return false;
        if (maxWidth  != null && v.getWidth()  > maxWidth)
        	return false;
        if (maxLength != null && v.getLength() > maxLength)
        	return false;

        return true;
    }
    
    @Override
    public int getPriority() {
    	
    }

	public DimensionLimitRule(Double maxWeight, Double maxHeight, Double maxWidth, Double maxLength,
			HashSet<VehicleType> affectedVehicles) {
		super();
		this.maxWeight = maxWeight;
		this.maxHeight = maxHeight;
		this.maxWidth = maxWidth;
		this.maxLength = maxLength;
		this.affectedVehicles = affectedVehicles;
	}
    
}

