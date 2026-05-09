package com.myteam.traffic.model.infrastructure.intersection;

import com.myteam.traffic.model.infrastructure.ConnectionPoint;
import com.myteam.traffic.model.infrastructure.IntersectionRenderData;
import com.myteam.traffic.model.infrastructure.IntersectionRenderData.ArmData;
import com.myteam.traffic.model.infrastructure.RoadSegment;
import com.myteam.traffic.model.infrastructure.Lane;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Lớp trừu tượng Intersection — khung sườn cho tất cả các loại nút giao thông.
 *
 * Cập nhật so với phiên bản trước:
 *  - connectRoad() nhận ConnectionPoint thay vì chỉ RoadSegment,
 *    để biết segment gắn vào đầu nào (START/END) và từ góc nào.
 *  - Thêm getRenderData() trả về dữ liệu thuần để UI vẽ,
 *    không import Graphics/Swing trong package này.
 *  - Giữ nguyên getConnectedRoads() để không break code cũ.
 */
public abstract class Intersection {

    private final double centerX;
    private final double centerY;

    /** Lưu ConnectionPoint thay vì RoadSegment thô — chứa đủ thông tin hình học. */
    private final List<ConnectionPoint> connections = new ArrayList<>();

    public Intersection(double centerX, double centerY) {
        this.centerX = centerX;
        this.centerY = centerY;
    }

    // ── Phương thức trừu tượng ───────────────────────────────

    public abstract int    getExpectedRoadCount();
    public abstract String getIntersectionType();

    // ── Quản lý kết nối ─────────────────────────────────────

    /**
     * Kết nối một đoạn đường vào nút giao, kèm thông tin đầu nối.
     *
     * @param cp ConnectionPoint mô tả segment + đầu nối (START/END)
     * @throws IllegalArgumentException nếu cp null
     * @throws IllegalStateException    nếu vượt quá số nhánh cho phép
     */
    public void connectRoad(ConnectionPoint cp) {
        if (cp == null) throw new IllegalArgumentException("ConnectionPoint không được null");
        if (connections.size() >= getExpectedRoadCount()) {
            throw new IllegalStateException(String.format(
                "Vượt quá số đường kết nối cho phép (%d) tại %s (%.1f, %.1f)",
                getExpectedRoadCount(), getIntersectionType(), centerX, centerY));
        }
        connections.add(cp);
    }

    /**
     * Overload tiện lợi: tự tạo ConnectionPoint từ segment + end.
     * Dùng khi không cần giữ tham chiếu ConnectionPoint.
     */
    public void connectRoad(RoadSegment road, ConnectionPoint.End end) {
        connectRoad(new ConnectionPoint(road, end));
    }

    public void disconnectRoad(RoadSegment road) {
        connections.removeIf(cp -> cp.getSegment() == road);
    }

    public boolean replaceRoad(RoadSegment oldRoad, RoadSegment newRoad) {
        for (int i = 0; i < connections.size(); i++) {
            if (connections.get(i).getSegment() == oldRoad) {
                ConnectionPoint old = connections.get(i);
                connections.set(i, new ConnectionPoint(newRoad, old.getEnd()));
                return true;
            }
        }
        return false;
    }

    // ── Getters ──────────────────────────────────────────────

    /** Tương thích ngược — trả về danh sách segment như cũ. */
    public List<RoadSegment> getConnectedRoads() {
        List<RoadSegment> list = new ArrayList<>();
        for (ConnectionPoint cp : connections) list.add(cp.getSegment());
        return Collections.unmodifiableList(list);
    }

    public List<ConnectionPoint> getConnections() {
        return Collections.unmodifiableList(connections);
    }

    public int    getRoadCount() { return connections.size(); }
    public double getCenterX()   { return centerX; }
    public double getCenterY()   { return centerY; }

    // ── Dữ liệu vẽ (Render Data) ─────────────────────────────

    /**
     * Trả về toàn bộ dữ liệu hình học cần thiết để UI vẽ nút giao này.
     *
     * UI KHÔNG cần biết gì về ConnectionPoint hay Lane —
     * chỉ cần dùng IntersectionRenderData là đủ.
     *
     * Cách dùng phía UI:
     * <pre>
     *   IntersectionRenderData d = intersection.getRenderData();
     *   drawCircle(d.centerX, d.centerY, d.radius);
     *   for (var arm : d.arms) {
     *       drawRoad(arm.tipX, arm.tipY, arm.approachAngleDeg, arm.totalWidth);
     *   }
     * </pre>
     */
    public IntersectionRenderData getRenderData() {
        List<ArmData> arms = new ArrayList<>();
        double maxWidth = 0;

        for (ConnectionPoint cp : connections) {
            RoadSegment seg = cp.getSegment();

            // Tính tổng chiều rộng tất cả làn của segment này
            double totalWidth = seg.getLanes().stream()
                    .mapToDouble(Lane::getWidth)
                    .sum();
            maxWidth = Math.max(maxWidth, totalWidth);

            // Điểm đầu nhánh = điểm chạm vào intersection
            double tipX = cp.getX();
            double tipY = cp.getY();

            arms.add(new ArmData(
                    tipX, tipY,
                    Math.toDegrees(cp.getApproachAngle()),
                    totalWidth,
                    seg.getLanes().size(),
                    seg
            ));
        }

        // Bán kính vùng nút giao = nửa chiều rộng lớn nhất, tối thiểu 20
        double radius = Math.max(20, maxWidth / 2.0);

        return new IntersectionRenderData(centerX, centerY, radius,
                getIntersectionType(), arms);
    }

    @Override
    public String toString() {
        return String.format("%s tại (%.1f, %.1f) — %d/%d đường",
                getIntersectionType(), centerX, centerY,
                connections.size(), getExpectedRoadCount());
    }
}