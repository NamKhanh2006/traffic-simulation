package com.myteam.traffic.vehicle.emergency;

import com.myteam.traffic.behavior.DriverBehavior;
import com.myteam.traffic.model.geometry.Direction;
import com.myteam.traffic.model.geometry.Position;
import com.myteam.traffic.vehicle.VehicleType;
import com.myteam.traffic.ui.SoundManager;

public class Ambulance extends EmergencyVehicle {

    public Ambulance(Position position, Direction direction, DriverBehavior behavior) {
        super(position, direction, behavior);
        
        this.setWidth(4.5);
        this.setHeight(2.2);
        this.setType(VehicleType.AMBULANCE);
    }

    @Override
    public void honk() {
        if (sirenOn) {
            SoundManager.playSound("ambulance_siren.mp3");
        } else {
            super.honk();
        }
    }
}