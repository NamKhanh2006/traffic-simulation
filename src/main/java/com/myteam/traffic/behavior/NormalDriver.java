package com.myteam.traffic.behavior;

import Traffic.vehicle.Vehicle;
import Traffic.traffic.TrafficLight;
import Traffic.behavior.common.*;

public class NormalDriver extends DriverBehavior {

    @Override
    public void decide(Vehicle self, Vehicle front, TrafficLight light) {

        DistanceKeeping.keepDistance(self, front);

        if (light.isRed()) {
            self.stop();
            System.out.println("Normal: dung den do");
            return;
        }

        if (front != null && front.isEmergency()) {
            self.slowDown();
            LaneChange.changeLane(self);
            System.out.println("Normal: nhuong duong xe uu tien");
        }

        self.moveForward();
    }
}
