package com.myteam.traffic.model.infrastructure.intersection;

import java.util.Map;
import java.util.HashMap;

/**
 * Lớp GeneralIntersection đại diện cho một nút giao thông tổng quát trong đô thị.
 * 
 * <p>Lớp này được thiết kế để xử lý linh hoạt các loại ngã ba, ngã tư cho đến các 
 * điểm giao cắt phức tạp hơn (ngã năm, ngã sáu...). Nó cung cấp các công cụ để 
 * phân tích mức độ phức tạp và các điểm xung đột tiềm tàng.</p>
 */
public class GeneralIntersection extends Intersection {

    private final int branchCount;  // Số lượng nhánh (đường) hội tụ tại nút giao
    private final String customName; // Tên tùy chỉnh (VD: "Ngã tư Hàng Xanh")

    /** Cache tên gọi phổ biến tiếng Việt dựa trên số nhánh để tối ưu hiển thị UI/Log. */
    private static final Map<Integer, String> TYPE_NAMES = new HashMap<>();
    static {
        TYPE_NAMES.put(3, "Ngã ba");
        TYPE_NAMES.put(4, "Ngã tư");
        TYPE_NAMES.put(5, "Ngã năm");
        TYPE_NAMES.put(6, "Ngã sáu");
        TYPE_NAMES.put(7, "Ngã bảy");
    }

    /**
     * Khởi tạo một giao lộ tổng quát dựa trên số nhánh.
     * 
     * @param centerX Tọa độ X của tâm nút giao.
     * @param centerY Tọa độ Y của tâm nút giao.
     * @param branchCount Số lượng nhánh kết nối (phải >= 3).
     */
    public GeneralIntersection(double centerX, double centerY, int branchCount) {
        this(centerX, centerY, branchCount, null);
    }

    /**
     * Khởi tạo một giao lộ tổng quát với tên gọi tùy chỉnh.
     * 
     * @param centerX Tọa độ X của tâm nút giao.
     * @param centerY Tọa độ Y của tâm nút giao.
     * @param branchCount Số lượng nhánh kết nối.
     * @param customName Tên riêng của nút giao (có thể null).
     * @throws IllegalArgumentException Nếu số nhánh nhỏ hơn 3 (không tạo thành giao lộ).
     */
    public GeneralIntersection(double centerX, double centerY, int branchCount, String customName) {
        super(centerX, centerY);
        if (branchCount < 3) {
            throw new IllegalArgumentException("Giao lộ phải có ít nhất 3 nhánh.");
        }
        this.branchCount = branchCount;
        this.customName = customName;
    }

    /**
     * Trả về số lượng đường tối đa được phép kết nối vào giao lộ này.
     * 
     * @return Số nhánh thiết kế.
     */
    @Override
    public int getExpectedRoadCount() {
        return branchCount;
    }

    /**
     * Xác định tên loại giao lộ. 
     * Ưu tiên sử dụng tên tùy chỉnh, sau đó đến tên phổ biến trong cache, 
     * cuối cùng là tên định danh theo số nhánh.
     * 
     * @return Chuỗi mô tả loại hoặc tên giao lộ.
     */
    @Override
    public String getIntersectionType() {
        if (customName != null) return customName;
        return TYPE_NAMES.getOrDefault(branchCount, "Giao lộ " + branchCount + " hướng");
    }

    /** 
     * Kiểm tra xem giao lộ có thuộc diện "độ phức tạp cao" hay không.
     * 
     * <p>Các giao lộ từ 5 nhánh trở lên (ngã năm, ngã sáu) thường yêu cầu logic 
     * điều khiển đèn tín hiệu phức tạp hơn hoặc phải tính toán kỹ hơn khi chuyển làn.</p>
     * 
     * @return true nếu số nhánh >= 5.
     */
    public boolean isHighComplexity() {
        return branchCount >= 5;
    }

    /** 
     * Ước tính số lượng điểm xung đột luồng xe tiềm tàng (Conflict Points).
     * 
     * <p>Số điểm xung đột tăng rất nhanh theo số nhánh. Công thức ước lượng cơ bản 
     * dựa trên tổ hợp các luồng xe có thể cắt nhau tại nút giao: $n \times (n - 1)$.
     * Chỉ số này giúp thuật toán định tuyến (Routing) đánh giá rủi ro hoặc độ trễ.</p>
     * 
     * @return Số lượng điểm xung đột ước tính.
     */
    public int getPotentialConflictPoints() {
        return branchCount * (branchCount - 1);
    }

    /**
     * Trả về chuỗi thông tin chi tiết phục vụ debug.
     */
    @Override
    public String toString() {
        return String.format("GeneralIntersection[Type=%s, Branches=%d, Pos=(%.1f, %.1f)]",
                getIntersectionType(), branchCount, centerX, centerY);
    }
}