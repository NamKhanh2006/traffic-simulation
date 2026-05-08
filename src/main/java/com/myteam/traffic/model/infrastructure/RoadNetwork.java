package com.myteam.traffic.model.infrastructure;

import com.myteam.traffic.model.infrastructure.intersection.Intersection;
import java.util.*;

/**
 * Lớp RoadNetwork đại diện cho toàn bộ hệ thống mạng lưới giao thông.
 * 
 * <p>Lớp này đóng vai trò là một "Container" (bộ chứa) quản lý tất cả các thực thể hạ tầng 
 * bao gồm các đoạn đường (RoadSegment) và các nút giao thông (Intersection). 
 * Nó cung cấp các phương thức để xây dựng và truy vấn cấu trúc của mạng lưới.</p>
 * 
 * <p>FIX #2: Khởi tạo các danh sách để tránh NullPointerException và quản lý tập trung.</p>
 */
public class RoadNetwork {
    
    // Danh sách lưu trữ các đoạn đường trong mạng lưới
    private final List<RoadSegment> segments = new ArrayList<>();
    
    // Danh sách lưu trữ các giao lộ (vòng xuyến, ngã tư, đèn giao thông...)
    private final List<Intersection> intersections = new ArrayList<>();

    /**
     * Thêm một đoạn đường mới vào mạng lưới.
     * Kiểm tra tính hợp lệ để đảm bảo không có dữ liệu null hoặc trùng lặp đơn giản.
     * 
     * @param segment Đối tượng RoadSegment cần thêm.
     * @throws IllegalArgumentException Nếu segment bị null.
     */
    public void addSegment(RoadSegment segment) {
        if (segment == null) {
            throw new IllegalArgumentException("Segment không được null");
        }
        // Kiểm tra tránh thêm trùng lặp cùng một tham chiếu đối tượng
        if (!segments.contains(segment)) {
            segments.add(segment);
        }
    }

    /**
     * Thêm một nút giao thông (Intersection) vào mạng lưới.
     * 
     * @param intersection Đối tượng Intersection cần thêm.
     * @throws IllegalArgumentException Nếu intersection bị null.
     */
    public void addIntersection(Intersection intersection) {
        if (intersection == null) {
            throw new IllegalArgumentException("Intersection không được null");
        }
        if (!intersections.contains(intersection)) {
            intersections.add(intersection);
        }
    }

    /**
     * Lấy danh sách tất cả các đoạn đường.
     * Sử dụng Collections.unmodifiableList để bảo vệ tính đóng gói (encapsulation),
     * ngăn chặn việc thay đổi danh sách trực tiếp từ bên ngoài mà không thông qua addSegment.
     * 
     * @return Một danh sách chỉ đọc (Read-only) của các RoadSegment.
     */
    public List<RoadSegment> getSegments() {
        return Collections.unmodifiableList(segments);
    }

    /**
     * Lấy danh sách tất cả các nút giao thông.
     * 
     * @return Một danh sách chỉ đọc (Read-only) của các Intersection.
     */
    public List<Intersection> getIntersections() {
        return Collections.unmodifiableList(intersections);
    }

    /**
     * Trả về tổng số lượng đoạn đường hiện có trong mạng lưới.
     */
    public int getSegmentCount() { 
        return segments.size(); 
    }

    /**
     * Trả về tổng số lượng nút giao thông hiện có trong mạng lưới.
     */
    public int getIntersectionCount() { 
        return intersections.size(); 
    }

    /**
     * Trả về chuỗi mô tả tóm tắt trạng thái của mạng lưới giao thông.
     * Hữu ích cho việc debug và logging.
     */
    @Override
    public String toString() {
        return String.format("RoadNetwork[segments=%d, intersections=%d]",
                segments.size(), intersections.size());
    }
}