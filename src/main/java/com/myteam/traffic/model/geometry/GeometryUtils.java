package com.myteam.traffic.model.geometry;

/**
 * Các hàm tiện ích hình học tĩnh phục vụ simulation giao thông.
 *
 * <p>Tất cả method đều là {@code static} — không cần khởi tạo class này.
 * Bao gồm các phép tính về đoạn thẳng, hình chữ nhật (bounding box),
 * và kiểm tra va chạm — dùng trong {@code MarkingRule}, {@code DistanceRule},
 * và hệ thống phát hiện va chạm.</p>
 */
public final class GeometryUtils {

    private GeometryUtils() {} // Ngăn khởi tạo

    // =========================================================
    // Kiểm tra giao nhau — dùng cho MarkingRule
    // =========================================================

    /**
     * Kiểm tra hai đoạn thẳng AB và CD có giao nhau không.
     *
     * <p>Dùng bởi {@code MarkingRule} để kiểm tra xe có cắt qua vạch kẻ
     * đường sau khi thực hiện action không.
     * Thuật toán dựa trên cross product — O(1).</p>
     *
     * <pre>
     * Ví dụ:
     *   segmentsIntersect(A(0,0), B(2,2), C(0,2), D(2,0)) → true  (giao chéo nhau)
     *   segmentsIntersect(A(0,0), B(1,1), C(2,2), D(3,3)) → false (song song, cùng phương)
     * </pre>
     */
    public static boolean segmentsIntersect(Position a, Position b,
                                            Position c, Position d) {
        Vector2D ab = Vector2D.between(a, b);
        Vector2D cd = Vector2D.between(c, d);
        Vector2D ac = Vector2D.between(a, c);

        double cross1 = ab.cross(cd);
        double t      = ac.cross(cd) / cross1;
        double u      = ac.cross(ab) / cross1;

        // Nếu cross1 == 0: hai đoạn song song (không giao)
        if (Math.abs(cross1) < 1e-10) return false;

        // Giao nhau khi cả t và u đều trong [0, 1]
        return t >= 0 && t <= 1 && u >= 0 && u <= 1;
    }

    // =========================================================
    // Khoảng cách từ điểm đến đoạn thẳng — dùng cho MarkingRule
    // =========================================================

    /**
     * Tính khoảng cách ngắn nhất từ điểm {@code p} đến đoạn thẳng {@code AB}.
     *
     * <p>Khác với khoảng cách đến đường thẳng vô hạn — phương thức này
     * đảm bảo điểm chiếu nằm trong đoạn AB.
     * Dùng để xác định xe có đang "gần vạch kẻ đường" không.</p>
     *
     * <pre>
     * Ví dụ:
     *   distanceToSegment(P(1,0), A(0,0), B(2,0)) → 0.0  (P nằm trên AB)
     *   distanceToSegment(P(3,0), A(0,0), B(2,0)) → 1.0  (P ngoài đoạn, gần B nhất)
     *   distanceToSegment(P(1,1), A(0,0), B(2,0)) → 1.0  (P vuông góc với AB)
     * </pre>
     */
    public static double distanceToSegment(Position p, Position a, Position b) {
        Vector2D ab = Vector2D.between(a, b);
        Vector2D ap = Vector2D.between(a, p);

        double abLenSq = ab.magnitudeSquared();

        // A và B trùng nhau → trả về khoảng cách P đến A
        if (abLenSq == 0) return ap.magnitude();

        // t là tham số chiếu: t=0 → gần A, t=1 → gần B
        double t = Math.max(0, Math.min(1, ap.dot(ab) / abLenSq));

        // Điểm chiếu trên đoạn AB
        Position projection = new Position(
            a.getX() + t * ab.getX(),
            a.getY() + t * ab.getY()
        );

        return p.distanceTo(projection);
    }

    // =========================================================
    // Bounding box (AABB) — dùng cho phát hiện va chạm sơ bộ
    // =========================================================

    /**
     * Kiểm tra hai hình chữ nhật (Axis-Aligned Bounding Box) có chồng lên nhau không.
     *
     * <p>Dùng như bước kiểm tra sơ bộ (broad phase) trước khi tính va chạm
     * chính xác hơn. Rất nhanh — O(1).</p>
     *
     * @param centerA Tâm hình chữ nhật A
     * @param halfWA  Nửa chiều rộng A
     * @param halfHA  Nửa chiều cao A
     * @param centerB Tâm hình chữ nhật B
     * @param halfWB  Nửa chiều rộng B
     * @param halfHB  Nửa chiều cao B
     */
    public static boolean aabbOverlap(Position centerA, double halfWA, double halfHA,
                                      Position centerB, double halfWB, double halfHB) {
        double dx = Math.abs(centerA.getX() - centerB.getX());
        double dy = Math.abs(centerA.getY() - centerB.getY());
        return dx < (halfWA + halfWB) && dy < (halfHA + halfHB);
    }

    // =========================================================
    // Nội suy tuyến tính — dùng cho animation / di chuyển mượt
    // =========================================================

    /**
     * Nội suy tuyến tính (lerp) giữa hai vị trí.
     *
     * <p>Dùng để tính vị trí trung gian khi render animation di chuyển
     * của xe giữa hai tick, tạo cảm giác mượt mà hơn.</p>
     *
     * @param from  Vị trí bắt đầu
     * @param to    Vị trí kết thúc
     * @param t     Tham số trong [0.0, 1.0] (0 = from, 1 = to)
     * @return Vị trí tại thời điểm t
     */
    public static Position lerp(Position from, Position to, double t) {
        t = Math.max(0, Math.min(1, t)); // clamp về [0, 1]
        return new Position(
            from.getX() + t * (to.getX() - from.getX()),
            from.getY() + t * (to.getY() - from.getY())
        );
    }

    /**
     * Nội suy tuyến tính giữa hai góc (degrees), xử lý đúng trường hợp
     * vòng qua 0°/360° (ví dụ: lerp từ 350° đến 10° qua 0°, không phải qua 180°).
     *
     * <p>Dùng để xoay sprite xe mượt mà khi chuyển hướng.</p>
     */
    public static double lerpAngle(double fromDeg, double toDeg, double t) {
        t = Math.max(0, Math.min(1, t));
        double delta = toDeg - fromDeg;

        // Chuẩn hóa delta về (-180, 180] để lấy đường ngắn nhất
        if (delta > 180)  delta -= 360;
        if (delta < -180) delta += 360;

        return fromDeg + t * delta;
    }

    // =========================================================
    // Góc và khoảng cách tiện ích
    // =========================================================

    /**
     * Tính góc (degrees) từ {@code from} nhìn về phía {@code to}.
     * Kết quả trong [0, 360), theo hệ tọa độ màn hình.
     */
    public static double angleBetweenPositions(Position from, Position to) {
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double deg = Math.toDegrees(Math.atan2(dy, dx));
        return (deg + 360) % 360;
    }

    /**
     * Kiểm tra {@code point} có nằm trong vòng tròn bán kính {@code radius}
     * tâm {@code center} không.
     *
     * <p>Dùng để xác định xe có đang nằm trong vùng ảnh hưởng của
     * đèn giao thông / biển báo không.</p>
     */
    public static boolean isWithinRadius(Position center, Position point, double radius) {
        return center.distanceTo(point) <= radius;
    }

    /**
     * Chuẩn hóa góc (degrees) về [0, 360).
     */
    public static double normalizeAngle(double degrees) {
        degrees = degrees % 360;
        if (degrees < 0) degrees += 360;
        return degrees;
    }
}