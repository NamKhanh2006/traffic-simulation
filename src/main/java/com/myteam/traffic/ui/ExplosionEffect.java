package com.myteam.traffic.ui;

/**
 * Data class lưu trạng thái của một hiệu ứng nổ.
 * Được tạo ra khi 2 xe va chạm thực sự.
 */
public class ExplosionEffect {

    /** Thời gian sống tối đa của hiệu ứng (giây). */
    public static final double LIFETIME = 1.0;

    public final double x;       // Tọa độ world X
    public final double y;       // Tọa độ world Y
    public double age = 0.0;     // Thời gian đã sống (giây)

    public ExplosionEffect(double x, double y) {
        this.x = x;
        this.y = y;
    }

    /** Trả về tỉ lệ tiến trình [0..1], 0 = vừa nổ, 1 = đã tắt. */
    public double progress() {
        return Math.min(1.0, age / LIFETIME);
    }

    /** true nếu hiệu ứng đã hết. */
    public boolean isDead() {
        return age >= LIFETIME;
    }
}
