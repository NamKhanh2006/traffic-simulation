package com.myteam.traffic.rule;

public class DimensionLimitRule implements TrafficRule {
    private int priority;
    // private double maxHeight; // in meters
    // private double maxWidth;  // in meters
    // private double maxLength; // in meters
    // private double maxWeight; // in tons

    public DimensionLimitRule(int priority, double maxHeight, double maxWidth, double maxLength) {
        this.priority = priority;
        // this.maxHeight = maxHeight;
        // this.maxWidth = maxWidth;
        // this.maxLength = maxLength;
        // this.maxWeight = maxWeight;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    // public double getMaxHeight() {
    //     return maxHeight;
    // }

    // public double getMaxWidth() {
    //     return maxWidth;
    // }

    // public double getMaxLength() {
    //     return maxLength;
    // }

    // public double getMaxWeight() {
    //     return maxWeight;
    // }
    
}
