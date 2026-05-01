package com.myteam.traffic.rule;

// Rule check for if vehicle is allow to cross the marking
public class MarkingRule implements TrafficRule {
    private int priority;
    // private String markingType; // e.g., "crosswalk", "stop line"

    public MarkingRule(int priority, String markingType) {
        this.priority = priority;
        // this.markingType = markingType;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    // public String getMarkingType() {
    //     return markingType;
    // }
    
}
