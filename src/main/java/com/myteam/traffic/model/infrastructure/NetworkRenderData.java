package com.myteam.traffic.model.infrastructure;

import com.myteam.traffic.model.infrastructure.intersection.Intersection;
import com.myteam.traffic.model.infrastructure.IntersectionRenderData;

import java.util.ArrayList;
import java.util.List;

/**
 * Gói dữ liệu để UI vẽ toàn bộ mạng lưới đường trong một lần gọi.
 * Đã được cập nhật để chứa IntersectionRenderData giúp UI vẽ dễ dàng hơn.
 */
public final class NetworkRenderData {

    /** Đổi tên từ 'segments' thành 'roads' để khớp với SimulationView */
    public final List<RoadSegment> roads;

    /** Chuyển danh sách logic Intersection sang danh sách dữ liệu hiển thị IntersectionRenderData */
    public final List<IntersectionRenderData> intersections;

    /** Bounding box của toàn bộ mạng lưới (world coordinates). */
    public final double minX, minY, maxX, maxY;

    public NetworkRenderData(List<RoadSegment> segments, List<Intersection> rawIntersections) {
        this.roads = segments;

        // Chuyển đổi từ Intersection (Logic) sang IntersectionRenderData (UI)
        this.intersections = new ArrayList<>();
        for (Intersection inter : rawIntersections) {
            this.intersections.add(inter.getRenderData());
        }

        // Tính bounding box từ tọa độ tất cả segment
        double mnX = Double.MAX_VALUE, mnY = Double.MAX_VALUE;
        double mxX = -Double.MAX_VALUE, mxY = -Double.MAX_VALUE;

        if (segments.isEmpty()) {
            this.minX = this.minY = this.maxX = this.maxY = 0;
        } else {
            for (RoadSegment s : segments) {
                mnX = Math.min(mnX, Math.min(s.getStartX(), s.getEndX()));
                mnY = Math.min(mnY, Math.min(s.getStartY(), s.getEndY()));
                mxX = Math.max(mxX, Math.max(s.getStartX(), s.getEndX()));
                mxY = Math.max(mxY, Math.max(s.getStartY(), s.getEndY()));
            }
            this.minX = mnX;
            this.minY = mnY;
            this.maxX = mxX;
            this.maxY = mxY;
        }
    }

    /** Chiều rộng tổng thể của mạng lưới. */
    public double getWorldWidth()  { return maxX - minX; }

    /** Chiều cao tổng thể của mạng lưới. */
    public double getWorldHeight() { return maxY - minY; }
}