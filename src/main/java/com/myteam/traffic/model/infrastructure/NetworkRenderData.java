package com.myteam.traffic.model.infrastructure;

import com.myteam.traffic.model.infrastructure.intersection.Intersection;
import com.myteam.traffic.model.infrastructure.IntersectionRenderData;

import java.util.List;

/**
 * Gói dữ liệu để UI vẽ toàn bộ mạng lưới đường trong một lần gọi.
 *
 * Tại sao cần class này?
 * Thay vì UI phải gọi network.getSegments() rồi network.getIntersections()
 * thành hai lần riêng, NetworkRenderData gom lại và tính sẵn bounding box
 * để UI biết cần scroll/zoom đến đâu.
 *
 * Cách UI sử dụng:
 * <pre>
 *   NetworkRenderData data = network.getRenderData();
 *
 *   // Vẽ tất cả đoạn đường
 *   for (RoadSegment seg : data.segments) {
 *       drawSegment(seg, scale);
 *   }
 *   // Vẽ tất cả nút giao
 *   for (Intersection inter : data.intersections) {
 *       drawIntersection(inter.getRenderData(), scale);
 *   }
 *   // Fit camera vào mạng lưới
 *   camera.fitTo(data.minX, data.minY, data.maxX, data.maxY);
 * </pre>
 */
public final class NetworkRenderData {

    /** Tất cả segment trong mạng lưới — UI tự vẽ từng cái. */
    public final List<RoadSegment>  segments;

    /** Tất cả intersection — gọi inter.getRenderData() để vẽ. */
    public final List<Intersection> intersections;

    /** Bounding box của toàn bộ mạng lưới (world coordinates). */
    public final double minX, minY, maxX, maxY;

    public NetworkRenderData(List<RoadSegment> segments, List<Intersection> intersections) {
        this.segments      = segments;
        this.intersections = intersections;

        // Tính bounding box từ tọa độ tất cả segment
        double mnX = Double.MAX_VALUE, mnY = Double.MAX_VALUE;
        double mxX = Double.MIN_VALUE, mxY = Double.MIN_VALUE;

        for (RoadSegment s : segments) {
            mnX = Math.min(mnX, Math.min(s.getStartX(), s.getEndX()));
            mnY = Math.min(mnY, Math.min(s.getStartY(), s.getEndY()));
            mxX = Math.max(mxX, Math.max(s.getStartX(), s.getEndX()));
            mxY = Math.max(mxY, Math.max(s.getStartY(), s.getEndY()));
        }

        this.minX = (segments.isEmpty()) ? 0 : mnX;
        this.minY = (segments.isEmpty()) ? 0 : mnY;
        this.maxX = (segments.isEmpty()) ? 0 : mxX;
        this.maxY = (segments.isEmpty()) ? 0 : mxY;
    }

    /** Chiều rộng tổng thể của mạng lưới. */
    public double getWorldWidth()  { return maxX - minX; }

    /** Chiều cao tổng thể của mạng lưới. */
    public double getWorldHeight() { return maxY - minY; }
}