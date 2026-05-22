package com.myteam.traffic.vehicle;

import com.myteam.traffic.behavior.DriverBehavior;

public class Car extends Vehicle { 

    public Car(double x, double y, double angle, DriverBehavior behavior) {
        super(x, y, angle, behavior);
        this.width = 4.0;
        this.height = 2.0; 
        this.maxSpeed = 60.0;
        this.isEmergency = false; 
        this.type = "Car";
    }

    @Override
    public void honk() {
        System.out.println("Bíp bíp! (Car)"); 
    }
}