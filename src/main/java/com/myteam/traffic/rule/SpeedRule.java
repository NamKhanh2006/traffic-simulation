package com.myteam.traffic.rule;

public class SpeedRule implements TrafficRule {
    private int priority;
    // private double maxSpeed; // in km/h

    public SpeedRule(int priority, double maxSpeed) {
        this.priority = priority;
        // this.maxSpeed = maxSpeed;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    // public double getMaxSpeed() {
    //     return maxSpeed;
    // }
    
}
