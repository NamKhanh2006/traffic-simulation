package com.myteam.traffic.model.infrastructure.intersection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.myteam.traffic.model.infrastructure.RoadSegment;

public abstract class Intersection {
    protected final double centerX, centerY;
    private final List<RoadSegment> connectedRoads = new ArrayList<>();

    public Intersection(double centerX, double centerY) {
        this.centerX = centerX;
        this.centerY = centerY;
    }

    public abstract int getExpectedRoadCount();
    public abstract String getIntersectionType();

    /**
     * FIX #1: Thay vì chỉ warn rồi vẫn add (silent error),
     * nay throw exception khi vượt quá số đường cho phép.
     * FIX #3: connectedRoads chuyển thành private, expose qua getter unmodifiable.
     */
    public void connectRoad(RoadSegment road) {
        if (road == null) throw new IllegalArgumentException("RoadSegment không được null");
        if (connectedRoads.size() >= getExpectedRoadCount()) {
            throw new IllegalStateException(
                    "Vượt quá số đường kết nối cho phép (" + getExpectedRoadCount() + ") tại " + getIntersectionType()
            );
        }
        connectedRoads.add(road);
    }

    /** FIX #3: Trả về unmodifiable view, không để ngoài mutate list. */
    public List<RoadSegment> getConnectedRoads() {
        return Collections.unmodifiableList(connectedRoads);
    }

    public double getCenterX() { return centerX; }
    public double getCenterY() { return centerY; }
}