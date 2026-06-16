package com.myteam.traffic.vehicle.emergency;

import com.myteam.traffic.behavior.DriverBehavior;
import com.myteam.traffic.model.geometry.Direction;
import com.myteam.traffic.model.geometry.Position;
import com.myteam.traffic.vehicle.Vehicle;
import com.myteam.traffic.ui.SoundManager;

public abstract class EmergencyVehicle extends Vehicle {
    protected boolean sirenOn; // Trạng thái còi ưu tiên

    public EmergencyVehicle(Position position, Direction direction, DriverBehavior behavior) {
        // Gọi constructor của lớp cha (Vehicle)
        super(position, direction, behavior);
        
        // Mặc định cho xe ưu tiên
        this.setEmergency(true);
        this.setMaxSpeed(80.0); // Tốc độ tối đa cao hơn xe thường
        this.sirenOn = true;  // Mặc định bật còi khi xuất hiện
    }

    public boolean isSirenOn() {
        return sirenOn;
    }

    public void setSirenOn(boolean sirenOn) {
        this.sirenOn = sirenOn;
    }

    // Bật/tắt còi ưu tiên
    public void toggleSiren() {
        this.sirenOn = !this.sirenOn;
    }

    @Override
    public void honk() {
        if (sirenOn) {
            SoundManager.playSound("ambulance_siren.mp3");
        } else {
            SoundManager.playSound("car.mp3");
        }
    }
}