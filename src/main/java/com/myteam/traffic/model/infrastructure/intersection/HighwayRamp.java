package com.myteam.traffic.model.infrastructure.intersection;

/**
 * Đại diện cho điểm nhập làn hoặc tách làn trên cao tốc.
 * Thường kết nối: 1 đường cao tốc chính và 1 đường dẫn (Ramp).
 */
public class HighwayRamp extends Intersection {

    public HighwayRamp(double centerX, double centerY) {
        super(centerX, centerY);
    }

    @Override
    public int getExpectedRoadCount() {
        // Một nút nhập làn thường nối 3 đoạn:
        // 2 đoạn của trục cao tốc chính (trước và sau điểm nhập)
        // và 1 đoạn đường dẫn (Ramp)
        return 3;
    }

    @Override
    public String getIntersectionType() {
        return "Highway Ramp / Interchange";
    }
}