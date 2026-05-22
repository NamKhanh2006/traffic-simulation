package com.myteam.traffic.vehicle.emergency;

import com.myteam.traffic.behavior.DriverBehavior;

public class FireTruck extends EmergencyVehicle {
     public FireTruck(double x, double y, double direction, DriverBehavior behavior) {
        super(x, y, direction, behavior);
        this.type = "FireTruck";
        this.width = 6.0;
        this.height = 2.5;
        this.maxSpeed = 70.0; // Chậm hơn cứu thương do xe nặng
    }
    
    @Override
    public void honk() {
        if (sirenOn) {
            System.out.println(type + " hú còi cứu hỏa: O e o e o e!!!");
        } else {
            super.honk();
        }
    }
}
