package com.myteam.traffic.vehicle;

/**
 * Chế độ di chuyển của phương tiện trên mạng lưới.
 */
public enum TravelMode {
    /** Đi trên {@link com.myteam.traffic.model.infrastructure.RoadSegment} với tiến độ {@code t}. */
    ON_SEGMENT,
    /** Đi theo quỹ đạo cung trong {@link com.myteam.traffic.model.infrastructure.intersection.Intersection}. */
    ON_INTERSECTION_PATH
}
