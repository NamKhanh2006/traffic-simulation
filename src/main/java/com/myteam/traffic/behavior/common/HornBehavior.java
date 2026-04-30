package com.myteam.traffic.behavior.common;

import Traffic.vehicle.Vehicle;

public class HornBehavior {
    public static void honk(Vehicle v) {
        System.out.println(v.getType() + " bam coi!");
    }
}
