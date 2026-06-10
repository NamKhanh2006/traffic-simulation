package com.myteam.traffic.vehicle;

import com.myteam.traffic.behavior.DriverBehavior;
import com.myteam.traffic.ui.SoundManager;

public class Bicycle extends Vehicle {

    public Bicycle(double x, double y, double angle, DriverBehavior behavior) {
        super(x, y, angle, behavior);
        
        this.setWidth(1.8);
        this.setHeight(0.6);
        this.setMaxSpeed(20.0);
        this.setEmergency(false);
        this.setType("Bicycle");
    }

    @Override
    public void honk() {
        System.out.println("Kính coong! (Bicycle)");
        SoundManager.playSound("bicycle_bell.wav");
    }
}