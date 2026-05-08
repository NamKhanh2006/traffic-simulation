package com.myteam.traffic.model.infrastructure;

import java.util.List;

public class HighwaySegment extends RoadSegment {
    private final double minSpeedLimit;
    private final boolean hasEmergencyLane;

    public HighwaySegment(double sx, double sy, double ex, double ey,
                          List<Lane> inputLanes, double minSpeedLimit, boolean hasEmergencyLane) {
        super(sx, sy, ex, ey, inputLanes);

        int requiredLanes = hasEmergencyLane ? 3 : 2;
        if (inputLanes.size() < requiredLanes) {
            throw new IllegalArgumentException("Cao tốc yêu cầu tối thiểu " + requiredLanes + " làn.");
        }
        // FIX #4: Validate minSpeedLimit không âm
        if (minSpeedLimit < 0) {
            throw new IllegalArgumentException("Tốc độ tối thiểu không được âm: " + minSpeedLimit);
        }
        this.minSpeedLimit = minSpeedLimit;
        this.hasEmergencyLane = hasEmergencyLane;
    }

    public boolean isBelowMinSpeed(double currentSpeed) {
        return currentSpeed < minSpeedLimit;
    }

    public boolean isEmergencyLane(int laneId) {
        if (!hasEmergencyLane) return false;
        return laneId == getLanes().get(getLanes().size() - 1).getIndex();
    }

    public double getMinSpeedLimit() { return minSpeedLimit; }
    public boolean hasEmergencyLane() { return hasEmergencyLane; }

    @Override
    public HighwaySegment withNewPoints(double sx, double sy, double ex, double ey) {
        return new HighwaySegment(sx, sy, ex, ey, getLanes(), minSpeedLimit, hasEmergencyLane);
    }
}