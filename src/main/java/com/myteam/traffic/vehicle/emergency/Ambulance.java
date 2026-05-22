package com.myteam.traffic.vehicle.emergency;

import com.myteam.traffic.behavior.DriverBehavior;

public class Ambulance extends EmergencyVehicle {

    public Ambulance(double x, double y, double direction, DriverBehavior behavior) {
        super(x, y, direction, behavior);
        this.setType("Ambulance");
        this.setWidth(4.5);
        this.setHeight(2.2);
    }

    @Override
    public void honk() {
        if (sirenOn) {
            System.out.println(this.getType() + " hú còi cấp cứu: Ní no ní no!!!");
        } else {
            System.out.println(this.getType() + " bíp bíp (chế độ thường)!");
        }
    }
}