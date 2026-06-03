package com.myteam.traffic.vehicle.emergency;

import com.myteam.traffic.behavior.DriverBehavior;
import com.myteam.traffic.model.geometry.Direction;
import com.myteam.traffic.model.geometry.Position;
import com.myteam.traffic.vehicle.Vehicle;

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
            System.out.println(this.getType() + " hú còi dẹp đường: Wee woo wee woo!!!");
        } else {
            System.out.println(this.getType() + " bíp bíp (chế độ thường)!");
        }
    }
}