package com.myteam.traffic.model.infrastructure.intersection;

/**
 * Lớp FourWayIntersection đại diện cho một ngã tư tiêu chuẩn (Crossroad).
 * 
 * <p>Đây là loại nút giao phổ biến nhất trong hệ thống giao thông đô thị, 
 * nơi hai con đường cắt nhau tạo thành 4 nhánh kết nối.</p>
 * 
 * <p>Giống như các lớp nút giao cụ thể khác, lớp này tận dụng tối đa logic 
 * từ lớp cha {@link Intersection} để quản lý dữ liệu và chỉ định nghĩa 
 * các đặc tính riêng biệt của ngã tư.</p>
 */
public class FourWayIntersection extends Intersection {

    /**
     * Khởi tạo một ngã tư tại vị trí tọa độ xác định.
     * 
     * @param centerX Tọa độ X của tâm ngã tư.
     * @param centerY Tọa độ Y của tâm ngã tư.
     */
    public FourWayIntersection(double centerX, double centerY) {
        // Chuyển tọa độ lên lớp cha để quản lý tập trung
        super(centerX, centerY);
    }

    /**
     * Trả về số lượng đường kết nối cố định cho một ngã tư tiêu chuẩn.
     * 
     * @return Luôn trả về 4.
     */
    @Override
    public int getExpectedRoadCount() {
        return 4;
    }

    /**
     * Trả về định danh loại nút giao là Ngã tư (Crossroad).
     */
    @Override
    public String getIntersectionType() {
        return "Four-way intersection (Crossroad)";
    }
}