package com.myteam.traffic.vehicle;

import com.myteam.traffic.behavior.DriverBehavior;
import com.myteam.traffic.model.geometry.*;

public class Motorbike extends Vehicle {

    public Motorbike(Position position, Direction direction, DriverBehavior behavior) {
        
        super(position, direction, behavior);
        
        
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