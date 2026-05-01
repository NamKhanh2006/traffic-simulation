package com.myteam.traffic.rule;

public class ActionRule implements TrafficRule {
    private int priority;
    private String action; // e.g., "turn left", "go straight", "stop"

    public ActionRule(int priority, String action) {
        this.priority = priority;
        this.action = action;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    public String getAction() {
        return action;
    }
    
}
