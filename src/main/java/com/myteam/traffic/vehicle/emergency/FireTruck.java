package com.myteam.traffic.vehicle.emergency;

import com.myteam.traffic.behavior.DriverBehavior;
import com.myteam.traffic.model.geometry.Direction;
import com.myteam.traffic.model.geometry.Position;
import com.myteam.traffic.vehicle.VehicleType;

public class FireTruck extends EmergencyVehicle {

    public FireTruck(Position position, Direction direction, DriverBehavior behavior) {
        super(position, direction, behavior);
        
        this.setWidth(6.0);
        this.setHeight(2.5);
        this.setType(VehicleType.EMERGENCY);
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