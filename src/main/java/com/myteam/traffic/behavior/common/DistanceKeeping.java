package com.myteam.traffic.behavior.common;

import Traffic.vehicle.Vehicle;

public class DistanceKeeping {
    public static void keepDistance(Vehicle self, Vehicle front) {
        if (front == null) return;

        double distance = front.getX() - self.getX();
        if (distance < 5) {
            self.slowDown();
            System.out.println(self.getType() + " giam toc giu khoang cach");
        }
    }
}
