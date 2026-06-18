package com.myteam.traffic.light;

import com.myteam.traffic.model.infrastructure.RoadSegment;

/**
 * Liên kết một đèn giao thông với một đoạn đường cụ thể.
 * Đèn này chỉ ảnh hưởng tới xe đang chạy trên segment đó.
 *
 * <p>Vị trí vẽ đèn: tại đầu cuối của segment theo hướng xe đi
 * (FORWARD → endX/endY, BACKWARD → startX/startY).</p>
 */
public class SegmentLight {

    private final RoadSegment segment;
    private final TrafficLight light;
    /** true = gắn vào đầu cuối (endX, endY); false = gắn vào đầu đầu (startX, startY) */
    private final boolean atEnd;

    public SegmentLight(RoadSegment segment, TrafficLight light, boolean atEnd) {
        this.segment = segment;
        this.light   = light;
        this.atEnd   = atEnd;
    }

    public RoadSegment getSegment() { return segment; }
    public TrafficLight getLight()  { return light; }
    public boolean isAtEnd()        { return atEnd; }

    /** Tọa độ world X của đèn. */
    public double getWorldX() {
        return atEnd ? segment.getEndX() : segment.getStartX();
    }

    /** Tọa độ world Y của đèn. */
    public double getWorldY() {
        return atEnd ? segment.getEndY() : segment.getStartY();
    }

    /** Góc vuông góc với đường (để offset đèn sang một bên). */
    public double getAngle() {
        return Math.atan2(segment.getEndY() - segment.getStartY(),
                          segment.getEndX() - segment.getStartX());
    }
}
