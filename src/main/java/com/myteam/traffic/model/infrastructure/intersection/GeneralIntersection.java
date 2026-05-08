package com.myteam.traffic.model.infrastructure.intersection;

import java.util.Map;
import java.util.HashMap;

/**
 * Giao lộ tổng quát cho n nhánh (n >= 3).
 * Sử dụng cho các ngã tư, ngã năm, hoặc các vòng xuyến phức tạp trong hệ thống.
 */
public class GeneralIntersection extends Intersection {

    private final int branchCount;
    private final String customName;

    // Cache tên gọi phổ biến để tối ưu hiệu năng
    private static final Map<Integer, String> TYPE_NAMES = new HashMap<>();
    static {
        TYPE_NAMES.put(3, "Ngã ba");
        TYPE_NAMES.put(4, "Ngã tư");
        TYPE_NAMES.put(5, "Ngã năm");
        TYPE_NAMES.put(6, "Ngã sáu");
        TYPE_NAMES.put(7, "Ngã bảy");
    }

    public GeneralIntersection(double centerX, double centerY, int branchCount) {
        this(centerX, centerY, branchCount, null);
    }

    public GeneralIntersection(double centerX, double centerY, int branchCount, String customName) {
        super(centerX, centerY);
        if (branchCount < 3) {
            throw new IllegalArgumentException("Giao lộ phải có ít nhất 3 nhánh.");
        }
        this.branchCount = branchCount;
        this.customName = customName;
    }

    @Override
    public int getExpectedRoadCount() {
        return branchCount;
    }

    @Override
    public String getIntersectionType() {
        if (customName != null) return customName;
        return TYPE_NAMES.getOrDefault(branchCount, "Giao lộ " + branchCount + " hướng");
    }

    /** * Kiểm tra độ phức tạp của giao lộ.
     * Hữu ích cho việc điều chỉnh AI hoặc thuật toán tìm đường.
     */
    public boolean isHighComplexity() {
        return branchCount >= 5;
    }

    /** * Ước tính số lượng điểm xung đột luồng xe.
     * Công thức: n * (n - 1)
     */
    public int getPotentialConflictPoints() {
        return branchCount * (branchCount - 1);
    }

    @Override
    public String toString() {
        return String.format("GeneralIntersection[Type=%s, Branches=%d, Pos=(%.1f, %.1f)]",
                getIntersectionType(), branchCount, centerX, centerY);
    }
}