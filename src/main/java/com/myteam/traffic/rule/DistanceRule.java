package com.myteam.traffic.rule;

public class DistanceRule implements TrafficRule {
    private int priority;
    // private double minDistance; // in meters

    public DistanceRule(int priority, double minDistance) {
        this.priority = priority;
        // this.minDistance = minDistance;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    // public double getMinDistance() {
    //     return minDistance;
    // }
    
}
