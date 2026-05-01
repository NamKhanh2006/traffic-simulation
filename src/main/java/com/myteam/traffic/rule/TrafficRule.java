package com.myteam.traffic.rule;

public interface TrafficRule {
//    boolean isAllowed(Vehicle v, Action a, RoadContext c);
    int getPriority(); // Higher number means higher priority
}
