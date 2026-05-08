package com.myteam.traffic.model.infrastructure.intersection;

/**
 * Lớp RoundaboutIntersection đại diện cho các vòng xuyến (đảo giao thông) trong hệ thống.
 * 
 * <p>Về mặt cấu trúc, vòng xuyến kế thừa từ {@link GeneralIntersection} vì nó có thể kết nối 
 * với số lượng nhánh (n) linh hoạt. Tuy nhiên, nó bổ sung các thuộc tính vật lý như bán kính 
 * và các quy tắc ưu tiên đặc thù giúp phương tiện di chuyển theo luồng tròn.</p>
 */
public class RoundaboutIntersection extends GeneralIntersection {

    /** Bán kính của đảo tròn trung tâm (đơn vị: mét). */
    private final double radius; 

    /**
     * Khởi tạo một nút giao kiểu vòng xuyến.
     * 
     * @param centerX Tọa độ X của tâm vòng xuyến.
     * @param centerY Tọa độ Y của tâm vòng xuyến.
     * @param branchCount Số lượng đường nhánh đi vào/ra khỏi vòng xuyến.
     * @param radius Bán kính đảo giao thông (phải > 0).
     * @throws IllegalArgumentException Nếu bán kính không hợp lệ.
     */
    public RoundaboutIntersection(double centerX, double centerY, int branchCount, double radius) {
        // Sử dụng constructor của GeneralIntersection với tên mặc định dựa trên số nhánh
        super(centerX, centerY, branchCount, "Vòng xuyến ngã " + branchCount);

        if (radius <= 0) {
            throw new IllegalArgumentException("Bán kính vòng xuyến phải lớn hơn 0");
        }
        this.radius = radius;
    }

    /**
     * Trả về định danh loại nút giao, bao gồm cả số lượng nhánh để dễ nhận diện.
     * 
     * @return Chuỗi mô tả loại vòng xuyến.
     */
    @Override
    public String getIntersectionType() {
        return String.format("Vòng xuyến đa nhánh (Ngã %d)", getExpectedRoadCount());
    }

    /**
     * Xác định quy tắc ưu tiên đặc thù của vòng xuyến.
     * 
     * <p>Trong mô phỏng giao thông, khi phương tiện tiếp cận vòng xuyến, phương thức này 
     *  xe bên trong vòng (vòng tròn nội tại) luôn có quyền ưu tiên 
     * cao hơn các xe đang đứng chờ nhập làn từ các nhánh ngoài.</p>
     * 
     * @return Luôn trả về true đối với mô hình vòng xuyến tiêu chuẩn.
     */
    public boolean hasInsidePriority() {
        return true;
    }

    /**
     * Lấy bán kính của vòng xuyến. 
     * Giá trị này thường dùng để tính toán quỹ đạo cong của phương tiện khi đi qua nút giao.
     */
    public double getRadius() {
        return radius;
    }

    /**
     * Trả về thông tin chi tiết phục vụ việc giám sát và debug hệ thống.
     */
    @Override
    public String toString() {
        return String.format("RoundaboutIntersection[Branches=%d, Radius=%.1fm, Pos=(%.1f, %.1f)]",
                getExpectedRoadCount(), radius, centerX, centerY);
    }
}