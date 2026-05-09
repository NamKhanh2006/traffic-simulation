package com.myteam.traffic.model.infrastructure.intersection;

import com.myteam.traffic.model.infrastructure.RoadSegment;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Lớp trừu tượng Intersection là khung sườn cho tất cả các loại nút giao thông.
 * 
 * <p>Nhiệm vụ chính của lớp này là:
 * <ul>
 *   <li>Quản lý vị trí địa lý của nút giao (tọa độ tâm).</li>
 *   <li>Quản lý danh sách các đoạn đường ({@link RoadSegment}) hội tụ tại đây.</li>
 *   <li>Đảm bảo tính toàn vẹn dữ liệu thông qua việc giới hạn số lượng kết nối.</li>
 * </ul>
 * </p>
 */
public abstract class Intersection {

    /** Tọa độ X tâm nút giao. Dùng private để đảm bảo tính bất biến sau khi khởi tạo. */
    private final double centerX;
    /** Tọa độ Y tâm nút giao. */
    private final double centerY;

    /** Danh sách các đoạn đường thực tế đang kết nối vào nút giao này. */
    private final List<RoadSegment> connectedRoads = new ArrayList<>();

    /**
     * Khởi tạo một nút giao tại vị trí xác định.
     */
    public Intersection(double centerX, double centerY) {
        this.centerX = centerX;
        this.centerY = centerY;
    }

    // ── Phương thức trừu tượng ───────────────────────────────

    /**
     * Trả về số lượng nhánh tối đa mà loại nút giao này hỗ trợ.
     * Ví dụ: Ngã tư trả về 4, Ngã ba trả về 3.
     */
    public abstract int getExpectedRoadCount();

    /**
     * Trả về tên mô tả loại nút giao (ví dụ: "Roundabout", "Crossroad").
     */
    public abstract String getIntersectionType();

    // ── Quản lý đường kết nối ────────────────────────────────

    /**
     * Thực hiện kết nối vật lý một đoạn đường vào nút giao.
     * 
     * <p>Cơ chế bảo vệ: Phương thức này sẽ kiểm tra số lượng đường hiện tại 
     * so với {@link #getExpectedRoadCount()}. Nếu vượt quá, một ngoại lệ sẽ được ném ra 
     * để tránh việc thiết lập bản đồ sai logic.</p>
     * 
     * @param road Đoạn đường cần kết nối.
     * @throws IllegalStateException Nếu số lượng đường kết nối vượt quá giới hạn thiết kế.
     */
    public void connectRoad(RoadSegment road) {
        if (road == null) throw new IllegalArgumentException("RoadSegment không được null");
        if (connectedRoads.size() >= getExpectedRoadCount()) {
            throw new IllegalStateException(String.format(
                "Vượt quá số đường kết nối cho phép (%d) tại %s (%.1f, %.1f)",
                getExpectedRoadCount(), getIntersectionType(), centerX, centerY));
        }
        connectedRoads.add(road);
    }

    /**
     * Gỡ bỏ kết nối của một đoạn đường.
     * Thường được gọi bởi hệ thống quản lý mạng lưới khi một con đường bị xóa.
     */
    public void disconnectRoad(RoadSegment road) {
        connectedRoads.remove(road);
    }

    /**
     * Thay thế một đoạn đường cũ bằng đoạn đường mới trong danh sách kết nối.
     * 
     * <p>Hành động này cực kỳ quan trọng khi thực hiện cập nhật hạ tầng (như mở rộng làn đường) 
     * mà không muốn phá vỡ cấu trúc của nút giao hiện tại.</p>
     * 
     * @return true nếu thay thế thành công, false nếu không tìm thấy đường cũ.
     */
    public boolean replaceRoad(RoadSegment oldRoad, RoadSegment newRoad) {
        int index = connectedRoads.indexOf(oldRoad);
        if (index == -1) return false;
        connectedRoads.set(index, newRoad);
        return true;
    }

    /** 
     * Trả về danh sách các đường kết nối dưới dạng Read-Only. 
     * Ngăn chặn các tác động thay đổi danh sách từ bên ngoài không qua kiểm soát.
     */
    public List<RoadSegment> getConnectedRoads() {
        return Collections.unmodifiableList(connectedRoads);
    }

    /** Trả về số lượng nhánh thực tế hiện có. */
    public int getRoadCount() {
        return connectedRoads.size();
    }

    // ── Getters ──────────────────────────────────────────────

    public double getCenterX() { return centerX; }
    public double getCenterY() { return centerY; }

    /**
     * Trả về chuỗi thông tin định danh nút giao phục vụ theo dõi hệ thống.
     */
    @Override
    public String toString() {
        return String.format("%s tại (%.1f, %.1f) — %d/%d đường",
                getIntersectionType(), centerX, centerY,
                connectedRoads.size(), getExpectedRoadCount());
    }
}