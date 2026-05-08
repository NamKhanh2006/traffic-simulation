package com.myteam.traffic.model.infrastructure;

import com.myteam.traffic.model.infrastructure.intersection.Intersection;
import java.util.*;

/**
 * FIX #2: RoadNetwork không còn là class rỗng.
 * Quản lý toàn bộ mạng lưới đường: danh sách segments và intersections.
 */
public class RoadNetwork {
    private final List<RoadSegment> segments = new ArrayList<>();
    private final List<Intersection> intersections = new ArrayList<>();

    public void addSegment(RoadSegment segment) {
        if (segment == null) throw new IllegalArgumentException("Segment không được null");
        if (!segments.contains(segment)) {
            segments.add(segment);
        }
    }

    public void addIntersection(Intersection intersection) {
        if (intersection == null) throw new IllegalArgumentException("Intersection không được null");
        if (!intersections.contains(intersection)) {
            intersections.add(intersection);
        }
    }

    public List<RoadSegment> getSegments() {
        return Collections.unmodifiableList(segments);
    }

    public List<Intersection> getIntersections() {
        return Collections.unmodifiableList(intersections);
    }

    public int getSegmentCount() { return segments.size(); }
    public int getIntersectionCount() { return intersections.size(); }

    @Override
    public String toString() {
        return String.format("RoadNetwork[segments=%d, intersections=%d]",
                segments.size(), intersections.size());
    }
}