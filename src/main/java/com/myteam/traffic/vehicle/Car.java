package com.myteam.traffic.vehicle;

import com.myteam.traffic.behavior.DriverBehavior;
import com.myteam.traffic.model.geometry.*;

public class Car extends Vehicle { 

    public Car(Position position, Direction direction, DriverBehavior behavior) {
        super(position, direction, behavior);
        this.setWidth(4.0);
        this.setHeight(2.0); 
        this.setMaxSpeed(60.0);
        this.setEmergency(false); 
        this.setType(VehicleType.CAR);
    }

    @Override
    public void honk() {
        System.out.println("Bíp bíp! (Car)"); 
    }
}