package com.myteam.traffic.model.infrastructure.intersection;

public class FiveWayIntersection extends Intersection {
    public FiveWayIntersection(double centerX, double centerY) {
        super(centerX, centerY);
    }

    @Override
    public int getExpectedRoadCount() {
        return 5;
    }

    @Override
    public String getIntersectionType() {
        return "Five-way intersection (Star-junction)";
    }
}