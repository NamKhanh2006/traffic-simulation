package com.myteam.traffic.rule;

public class VehicleFilterRule implements TrafficRule {
    private int priority;
    // private String vehicleType;

    public VehicleFilterRule(int priority, String vehicleType) {
        this.priority = priority;
        // this.vehicleType = vehicleType;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    // public String getVehicleType() {
    //     return vehicleType;
    // }
    
}