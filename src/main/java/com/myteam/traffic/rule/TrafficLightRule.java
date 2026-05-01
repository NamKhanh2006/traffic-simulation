package com.myteam.traffic.rule;

public class TrafficLightRule implements TrafficRule {
    private int priority;
    // private int lightColor;

    public TrafficLightRule(int priority, int lightColor) {
        this.priority = priority;
        // this.lightColor = lightColor;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    // public int getLightColor() {
    //     return lightColor;
    // }
    
}
