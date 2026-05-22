package com.myteam.traffic.vehicle.emergency;

import com.myteam.traffic.behavior.DriverBehavior;

public class Ambulance extends EmergencyVehicle {

    public Ambulance(double x, double y, double angle, DriverBehavior behavior) {
        super(x, y, angle, behavior);
        
        this.setWidth(4.5);
        this.setHeight(2.2);
        this.setType("Ambulance");
    }
}