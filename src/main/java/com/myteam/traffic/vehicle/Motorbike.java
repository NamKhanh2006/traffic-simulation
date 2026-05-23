package com.myteam.traffic.vehicle;

import com.myteam.traffic.behavior.DriverBehavior;

public class Motorbike extends Vehicle {

    public Motorbike(double x, double y, double angle, DriverBehavior behavior) {
        
        super(x, y, angle, behavior);
        
        
        this.setWidth(2.0);      
        this.setHeight(1.0);
        this.setMaxSpeed(50.0);  
        this.setEmergency(false);
        this.setType(VehicleType.MOTORBIKE);
    }

    @Override
    public void honk() {
        System.out.println("Tít tít! (Motorbike)");
    }
}