package com.myteam.traffic.vehicle;

import com.myteam.traffic.behavior.DriverBehavior;

public class Car extends Vehicle { 

    public Car(double x, double y, double angle, DriverBehavior behavior) {
        super(x, y, angle, behavior);
        this.setWidth(4.0);
        this.setHeight(2.0); 
        this.setMaxSpeed(60.0);
        this.setEmergency(false); 
        this.setType("Car");
    }

    @Override
    public void honk() {
        System.out.println("Bíp bíp! (Car)"); 
    }
}