package com.myteam.traffic.vehicle.emergency;

import com.myteam.traffic.vehicle.Vehicle;
import com.myteam.traffic.behavior.DriverBehavior;

/**
 * Lớp cơ sở cho các phương tiện ưu tiên.
 * Do Hùng phụ trách.
 */
public abstract class EmergencyVehicle extends Vehicle {
    protected boolean sirenOn; // Trạng thái đang hú còi/bật đèn nháy

    public EmergencyVehicle(double x, double y, double direction, DriverBehavior behavior) {
        super(x, y, direction, behavior);
        this.setEmergency(true);
        this.setMaxSpeed(80.0);
        this.sirenOn = true;
    }

    public boolean isSirenOn() {
        return sirenOn;
    }

    public void toggleSiren() {
        this.sirenOn = !this.sirenOn;
    }

    @Override
    public void honk() {
       
    }
}