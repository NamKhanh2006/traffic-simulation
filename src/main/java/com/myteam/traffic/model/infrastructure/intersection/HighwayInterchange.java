package com.myteam.traffic.model.infrastructure.intersection;

/**
 * HighwayInterchange đại diện cho nút giao khác mức trên đường cao tốc.
 * 
 * <p>Đây là loại nút giao có cấu trúc phức tạp, thường gồm nhiều nhánh
 * ra vào như đường nối (ramp), cầu vượt hoặc hầm chui.</p>
 * 
 * <p>Khác với ngã tư thông thường có số hướng cố định,
 * nút giao cao tốc có thể có số lượng đường kết nối khác nhau
 * tùy theo thiết kế thực tế.</p>
 */
public class HighwayInterchange extends Intersection {
    
    /**
     * Số lượng đường tối đa có thể kết nối vào nút giao.
     */
    private final int capacity;

    /**
     * Tạo một nút giao cao tốc.
     *
     * @param centerX tọa độ X của tâm nút giao
     * @param centerY tọa độ Y của tâm nút giao
     * @param capacity số lượng đường tối đa được phép kết nối
     */
    public HighwayInterchange(double centerX, double centerY, int capacity) {
        // Khởi tạo vị trí nút giao từ lớp cha
        super(centerX, centerY);
        this.capacity = capacity;
    }

    /**
     * Trả về số lượng đường tối đa mà nút giao hỗ trợ.
     *
     * @return số lượng đường kết nối tối đa
     */
    @Override
    public int getExpectedRoadCount() {
        return capacity;
    }

    /**
     * Trả về tên loại nút giao.
     *
     * @return chuỗi mô tả loại nút giao
     */
    @Override
    public String getIntersectionType() {
        return "Complex Highway Interchange";
    }

    /**
     * Lấy sức chứa hiện tại của nút giao.
     *
     * @return số lượng đường tối đa có thể kết nối
     */
    public int getCapacity() {
        return capacity;
    }
}