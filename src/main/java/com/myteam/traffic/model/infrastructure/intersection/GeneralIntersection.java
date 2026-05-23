package com.myteam.traffic.model.infrastructure.intersection;

import java.util.HashMap;
import java.util.Map;

/**
 * Lớp GeneralIntersection đại diện cho một nút giao thông tổng quát với số nhánh linh hoạt.
 * 
 * <p>Lớp này được sử dụng cho các trường hợp không cần định nghĩa hình học đặc thù (như T-shape hay Roundabout),
 * hoặc cho các nút giao có số nhánh lớn (ngã sáu, ngã bảy) nơi logic điều khiển chủ yếu dựa trên 
 * số lượng luồng xung đột.</p>
 */
public class GeneralIntersection extends Intersection {

    private final int branchCount;  // Số lượng nhánh (đường) hội tụ
    private final String customName; // Tên riêng (ví dụ: "Vòng xoay Quách Thị Trang")

    /** 
     * Bản đồ tra cứu tên gọi phổ biến tiếng Việt dựa trên số lượng nhánh. 
     * Giúp UI hiển thị thân thiện hơn thay vì chỉ hiện con số.
     */
    private static final Map<Integer, String> TYPE_NAMES = new HashMap<>();
    static {
        TYPE_NAMES.put(3, "Ngã ba");
        TYPE_NAMES.put(4, "Ngã tư");
        TYPE_NAMES.put(5, "Ngã năm");
        TYPE_NAMES.put(6, "Ngã sáu");
        TYPE_NAMES.put(7, "Ngã bảy");
    }

    /**
     * Khởi tạo giao lộ tổng quát theo số nhánh.
     * 
     * @param centerX Tọa độ X của tâm giao lộ.
     * @param centerY Tọa độ Y của tâm giao lộ.
     * @param branchCount Số nhánh kết nối (tối thiểu là 3).
     */
    public GeneralIntersection(double centerX, double centerY, int branchCount) {
        this(centerX, centerY, branchCount, null);
    }

    /**
     * Khởi tạo giao lộ tổng quát với tên tùy chỉnh.
     * 
     * @param centerX Tọa độ X của tâm giao lộ.
     * @param centerY Tọa độ Y của tâm giao lộ.
     * @param branchCount Số nhánh kết nối.
     * @param customName Tên riêng của giao lộ.
     * @throws IllegalArgumentException Nếu branchCount < 3.
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
     * Trả về số lượng đường nhánh dự kiến sẽ kết nối vào giao lộ này.
     */
    @Override
    public int getExpectedRoadCount() {
        return branchCount;
    }

    /**
     * Lấy tên định danh của loại giao lộ.
     * 
     * @return Tên tùy chỉnh nếu có, nếu không sẽ tra cứu từ TYPE_NAMES hoặc trả về chuỗi mặc định.
     */
    @Override
    public String getIntersectionType() {
        if (customName != null) return customName;
        return TYPE_NAMES.getOrDefault(branchCount, "Giao lộ " + branchCount + " hướng");
    }

    /** 
     * Đánh giá độ phức tạp của giao lộ.
     * 
     * <p>Các giao lộ có từ 5 nhánh trở lên được coi là phức tạp cao, thường yêu cầu 
     * pha đèn tín hiệu (Signal Phases) nhiều hơn và AI cần tính toán kỹ hơn khi nhập làn.</p>
     * 
     * @return true nếu số nhánh >= 5.
     */
    public boolean isHighComplexity() {
        return branchCount >= 5;
    }

    /**
     * Ước tính số điểm xung đột (Conflict Points) luồng xe tiềm tàng.
     * 
     * <p>Công thức cơ bản: $P = n \times (n - 1)$. 
     * Trong đó $n$ là số nhánh. Đây là chỉ số quan trọng để thuật toán tìm đường (Routing) 
     * đánh giá mức độ rủi ro và khả năng xảy ra ùn tắc tại nút giao.</p>
     * 
     * @return Số lượng điểm xung đột ước tính.
     */
    public int getPotentialConflictPoints() {
        return branchCount * (branchCount - 1);
    }

    /**
     * Trả về chuỗi mô tả trạng thái giao lộ, sử dụng Getter từ lớp cha để lấy tọa độ.
     */
    @Override
    public String toString() {
        return String.format("GeneralIntersection[Type=%s, Branches=%d, Pos=(%.1f, %.1f)]",
                getIntersectionType(), branchCount, getCenterX(), getCenterY());
    }
}