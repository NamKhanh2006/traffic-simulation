package com.myteam.traffic.vehicle.emergency;

import com.myteam.traffic.behavior.DriverBehavior;
import com.myteam.traffic.model.geometry.Direction;
import com.myteam.traffic.model.geometry.Position;
import com.myteam.traffic.vehicle.VehicleType;
import com.myteam.traffic.ui.SoundManager;

public class FireTruck extends EmergencyVehicle {

    public FireTruck(Position position, Direction direction, DriverBehavior behavior) {
        super(position, direction, behavior);
        
        this.setWidth(6.0);
        this.setHeight(2.5);
        this.setType(VehicleType.FIRETRUCK);
        this.setMaxSpeed(70.0); // Chậm do cứu thương do nặng
    }

    @Override
    public void honk() {
        if (sirenOn) {
            SoundManager.playSound("firetruck_siren.mp3");
            // Ghi chú cho Team GUI: Khi còi bật, hiển thị hiệu ứng đèn quay chớp
        } else {
            super.honk();
        }
    }
}