package com.myteam.traffic.rule;

public class HornRule implements TrafficRule {
    private int priority;
    // private boolean hornAllowed;

    public HornRule(int priority, boolean hornAllowed) {
        this.priority = priority;
        // this.hornAllowed = hornAllowed;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    // public boolean isHornAllowed() {
    //     return hornAllowed;
    // }
    
}
