package com.myteam.traffic.model.infrastructure;

import java.util.List;

/**
 * Gói dữ liệu hình học để UI vẽ một Intersection.
 *
 * Nguyên tắc: Infrastructure KHÔNG import Graphics/Swing.
 * Class này chỉ chứa số và String — UI tự quyết định vẽ thế nào.
 *
 * UI dùng như sau:
 *   IntersectionRenderData d = intersection.getRenderData();
 *   // Vẽ vùng nút giao
 *   drawCircle(d.centerX, d.centerY, d.radius);
 *   // Vẽ từng nhánh đường
 *   for (var arm : d.arms) {
 *       drawRoad(arm.tipX, arm.tipY, arm.approachAngleDeg, arm.totalWidthPixels);
 *   }
 */
public final class IntersectionRenderData {

    /** Tọa độ tâm nút giao (world coordinates). */
    public final double centerX, centerY;

    /** Bán kính vùng nút giao (tổng chiều rộng tất cả làn / 2, tối thiểu 20). */
    public final double radius;

    /** Tên loại nút giao để vẽ label. */
    public final String typeName;

    /** Danh sách các "cánh tay" đường kết nối vào nút giao. */
    public final List<ArmData> arms;

    public IntersectionRenderData(double centerX, double centerY, double radius,
                                  String typeName, List<ArmData> arms) {
        this.centerX  = centerX;
        this.centerY  = centerY;
        this.radius   = radius;
        this.typeName = typeName;
        this.arms     = List.copyOf(arms); // immutable
    }

    // ─────────────────────────────────────────────────────────

    /**
     * Dữ liệu một nhánh đường tại nút giao.
     * UI dùng tipX/tipY làm điểm bắt đầu vẽ đoạn đường ra ngoài.
     */
    public static final class ArmData {

        /** Điểm đầu nhánh (cạnh nút giao, không phải tim đường). */
        public final double tipX, tipY;

        /** Góc tiếp cận (độ, 0° = Đông, 90° = Nam theo màn hình). */
        public final double approachAngleDeg;

        /** Tổng chiều rộng tất cả làn của segment này (pixels/meters). */
        public final double totalWidth;

        /** Số làn của segment tại nhánh này. */
        public final int laneCount;

        /** Segment gốc — UI có thể query thêm nếu cần. */
        public final RoadSegment segment;

        public ArmData(double tipX, double tipY, double approachAngleDeg,
                       double totalWidth, int laneCount, RoadSegment segment) {
            this.tipX            = tipX;
            this.tipY            = tipY;
            this.approachAngleDeg = approachAngleDeg;
            this.totalWidth      = totalWidth;
            this.laneCount       = laneCount;
            this.segment         = segment;
        }
    }
}