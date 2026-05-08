package com.myteam.traffic.model.policy;

import java.util.Objects;

/**
 * Lớp SpeedPolicy định nghĩa các quy tắc về tốc độ áp dụng cho đường bộ hoặc làn đường.
 * 
 * <p>Lớp này không chỉ quản lý giới hạn tốc độ cứng (limit) mà còn hỗ trợ khái niệm 
 * "ngưỡng dung sai" (tolerance), giúp mô phỏng thực tế hơn khi các phương tiện 
 * hoặc hệ thống giám sát thường có một khoảng sai số trước khi xác định vi phạm.</p>
 */
public class SpeedPolicy {
    
    /** Giới hạn tốc độ biển báo (km/h). */
    private final double limit;
    
    /** 
     * Ngưỡng dung sai cho phép (km/h). 
     * Ví dụ: limit = 60, tolerance = 5 -> Xe chạy đến 65km/h chưa bị coi là vi phạm.
     */
    private final double tolerance;

    /**
     * Khởi tạo một chính sách tốc độ mới.
     * 
     * @param limit Tốc độ giới hạn (tự động điều chỉnh về 0 nếu giá trị âm).
     * @param tolerance Khoảng dung sai cho phép (tự động điều chỉnh về 0 nếu giá trị âm).
     */
    public SpeedPolicy(double limit, double tolerance) {
        this.limit = Math.max(0, limit);
        this.tolerance = Math.max(0, tolerance);
    }

    /** Trả về tốc độ giới hạn được ghi trên biển báo. */
    public double getLimit() { 
        return limit; 
    }

    /** 
     * Trả về giới hạn tốc độ thực tế (Limit + Tolerance).
     * Đây là ngưỡng mà hệ thống mô phỏng dùng để kiểm tra vi phạm hoặc điều khiển AI.
     */
    public double getEffectiveLimit() { 
        return limit + tolerance; 
    }

    /**
     * Kiểm tra xem một phương tiện có đang vi phạm tốc độ hay không.
     * 
     * @param speed Tốc độ hiện tại của phương tiện.
     * @return true nếu tốc độ vượt quá giới hạn hiệu dụng (effective limit).
     */
    public boolean isViolation(double speed) {
        return speed > getEffectiveLimit();
    }

    /**
     * So sánh hai chính sách tốc độ dựa trên giá trị limit và tolerance.
     * 
     * <p>FIX #6: Việc ghi đè equals giúp hệ thống tránh tạo ra nhiều bản ghi trùng lặp
     * trong bộ nhớ khi gán cùng một loại chính sách cho nhiều làn đường khác nhau.</p>
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SpeedPolicy other)) return false;
        // Sử dụng Double.compare để xử lý các vấn đề chính xác của số thực (NaN, Infinity)
        return Double.compare(limit, other.limit) == 0
                && Double.compare(tolerance, other.tolerance) == 0;
    }

    /** Tạo mã băm dựa trên các thông số tốc độ. */
    @Override
    public int hashCode() {
        return Objects.hash(limit, tolerance);
    }

    /**
     * Trả về thông tin chi tiết của chính sách tốc độ.
     * Hữu ích cho việc hiển thị thông báo vi phạm hoặc debug hệ thống.
     */
    @Override
    public String toString() {
        return String.format("SpeedPolicy[limit=%.1f, tolerance=%.1f, effective=%.1f km/h]",
                limit, tolerance, getEffectiveLimit());
    }
}