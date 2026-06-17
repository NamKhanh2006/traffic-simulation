package com.myteam.traffic.vehicle;

import com.myteam.traffic.behavior.DriverBehavior;
import com.myteam.traffic.model.geometry.*;
import com.myteam.traffic.ui.SoundManager;

public class Bicycle extends Vehicle {

    public Bicycle(Position position, Direction direction, DriverBehavior behavior) {
        super(position, direction, behavior);

        this.setWidth(1.8);
        this.setHeight(0.6);
        this.setMaxSpeed(10.0);
        this.setEmergency(false);
        this.setType(VehicleType.BICYCLE);
    }

    @Override
    public void honk() {
        SoundManager.playSound("bicycle_bell.mp3");
    }
}