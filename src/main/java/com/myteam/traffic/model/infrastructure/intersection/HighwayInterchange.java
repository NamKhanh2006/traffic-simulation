package com.myteam.traffic.model.infrastructure.intersection;

public class HighwayInterchange extends Intersection {
    private final int capacity;

    public HighwayInterchange(double centerX, double centerY, int capacity) {
        super(centerX, centerY);
        this.capacity = capacity;
    }

    @Override
    public int getExpectedRoadCount() {
        return capacity;
    }

    @Override
    public String getIntersectionType() {
        return "Complex Highway Interchange";
    }
}