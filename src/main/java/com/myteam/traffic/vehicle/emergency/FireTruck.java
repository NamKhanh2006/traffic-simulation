package com.myteam.traffic.vehicle.emergency;

import com.myteam.traffic.behavior.DriverBehavior;

public class FireTruck extends EmergencyVehicle {

    public FireTruck(double x, double y, double angle, DriverBehavior behavior) {
        super(x, y, angle, behavior);
        
        this.setWidth(6.0);
        this.setHeight(2.5);
        this.setType("FireTruck");
        this.setMaxSpeed(70.0); // Chậm do cứu thương do nặng
    }

    @Override
    public void honk() {
        if (sirenOn) {
            System.out.println(this.getType() + " hú còi dẹp đường: O e o e o e!!!");
            // Ghi chú cho Team GUI: Khi còi bật, hiển thị hiệu ứng đèn quay chớp
        } else {
            super.honk();
        }
    }
}