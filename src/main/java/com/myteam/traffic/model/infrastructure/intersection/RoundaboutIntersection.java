package com.myteam.traffic.model.infrastructure.intersection;

/**
 * Lớp đại diện cho các vòng xuyến (Roundabout).
 * Kế thừa GeneralIntersection để quản lý số lượng nhánh kết nối linh hoạt.
 */
public class RoundaboutIntersection extends GeneralIntersection {

    private final double radius; // Bán kính vòng xuyến (m)

    public RoundaboutIntersection(double centerX, double centerY, int branchCount, double radius) {
        // Sử dụng super để thiết lập các thông số cơ bản từ GeneralIntersection
        super(centerX, centerY, branchCount, "Vòng xuyến ngã " + branchCount);

        if (radius <= 0) {
            throw new IllegalArgumentException("Bán kính vòng xuyến phải lớn hơn 0");
        }
        this.radius = radius;
    }

    @Override
    public String getIntersectionType() {
        return String.format("Vòng xuyến đa nhánh (Ngã %d)", getExpectedRoadCount());
    }

    /**
     * Logic đặc thù: Trong vòng xuyến, các xe phải ưu tiên xe đang di chuyển bên trong vòng.
     * Phương thức này có thể được AI của phương tiện gọi để quyết định hành vi nhập làn.
     */
    public boolean hasInsidePriority() {
        return true;
    }

    public double getRadius() {
        return radius;
    }

    @Override
    public String toString() {
        return String.format("RoundaboutIntersection[Branches=%d, Radius=%.1fm, Pos=(%.1f, %.1f)]",
                getExpectedRoadCount(), radius, centerX, centerY);
    }
}