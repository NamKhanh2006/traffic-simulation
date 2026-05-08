package com.myteam.traffic.model.infrastructure.intersection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.myteam.traffic.model.infrastructure.RoadSegment;

/**
 * Lớp trừu tượng Intersection đại diện cho một nút giao thông trong mạng lưới đường bộ.
 * 
 * <p>Vai trò chính của Intersection là:
 * <ul>
 *     <li>Xác định vị trí địa lý của nút giao (tọa độ trung tâm).</li>
 *     <li>Quản lý danh sách các đoạn đường (RoadSegment) hội tụ hoặc xuất phát từ nút giao này.</li>
 *     <li>Định nghĩa các ràng buộc về số lượng kết nối tối đa tùy theo loại nút giao cụ thể.</li>
 * </ul>
 */
public abstract class Intersection {
    
    /** Tọa độ X và Y tại tâm của nút giao. */
    protected final double centerX, centerY;
    
    /** 
     * Danh sách các đoạn đường kết nối vào nút giao này.
     * Được để ở trạng thái private để đảm bảo tính đóng gói tối đa.
     */
    private final List<RoadSegment> connectedRoads = new ArrayList<>();

    /**
     * Khởi tạo một nút giao tại tọa độ xác định.
     * 
     * @param centerX Tọa độ X của tâm nút giao.
     * @param centerY Tọa độ Y của tâm nút giao.
     */
    public Intersection(double centerX, double centerY) {
        this.centerX = centerX;
        this.centerY = centerY;
    }

    /**
     * Trả về số lượng đoạn đường tối đa mà loại nút giao này hỗ trợ.
     * Ví dụ: Ngã tư (Crossroad) sẽ trả về 4, Ngã ba (T-Junction) trả về 3.
     * 
     * @return Số lượng đường kết nối dự kiến.
     */
    public abstract int getExpectedRoadCount();

    /**
     * Trả về tên định danh loại nút giao (ví dụ: "Roundabout", "Signalized Intersection").
     * 
     * @return Chuỗi mô tả loại nút giao.
     */
    public abstract String getIntersectionType();

    /**
     * Thực hiện kết nối một đoạn đường vào nút giao này.
     * 
     * <p>FIX #1: Phương thức này thực thi kiểm tra ràng buộc nghiêm ngặt. Nếu số lượng đường
     * vượt quá giới hạn thiết kế của loại nút giao đó, một ngoại lệ sẽ được ném ra thay vì
     * chỉ cảnh báo mập mờ.</p>
     * 
     * @param road Đoạn đường (RoadSegment) cần kết nối.
     * @throws IllegalArgumentException Nếu đối tượng road truyền vào bị null.
     * @throws IllegalStateException Nếu nút giao đã đạt giới hạn kết nối tối đa.
     */
    public void connectRoad(RoadSegment road) {
        if (road == null) {
            throw new IllegalArgumentException("RoadSegment không được null");
        }
        
        if (connectedRoads.size() >= getExpectedRoadCount()) {
            throw new IllegalStateException(
                    String.format("Vượt quá số đường kết nối cho phép (%d) tại %s ở tọa độ (%.1f, %.1f)", 
                    getExpectedRoadCount(), getIntersectionType(), centerX, centerY)
            );
        }
        connectedRoads.add(road);
    }

    /** 
     * Truy vấn danh sách các đoạn đường đang kết nối với nút giao này.
     * 
     * <p>FIX #3: Sử dụng Collections.unmodifiableList để ngăn chặn mã bên ngoài 
     * can thiệp trực tiếp vào danh sách nội bộ (ví dụ: tự ý xóa đường khỏi danh sách).</p>
     * 
     * @return Danh sách RoadSegment ở chế độ chỉ đọc (Read-only).
     */
    public List<RoadSegment> getConnectedRoads() {
        return Collections.unmodifiableList(connectedRoads);
    }

    // ── Getters cơ bản ────────────────────────────────────────

    /** Lấy tọa độ X của tâm nút giao. */
    public double getCenterX() { return centerX; }

    /** Lấy tọa độ Y của tâm nút giao. */
    public double getCenterY() { return centerY; }
}