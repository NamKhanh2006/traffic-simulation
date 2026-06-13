package com.myteam.traffic.model.geometry;
 
/**
 * Vector 2 chiều bất biến (immutable), dùng để biểu diễn:
 * <ul>
 *   <li>Hướng và tốc độ di chuyển của xe (velocity vector)</li>
 *   <li>Độ dịch chuyển giữa hai vị trí (displacement vector)</li>
 *   <li>Vector pháp tuyến của vạch kẻ đường (normal vector)</li>
 * </ul>
 *
 * Hệ tọa độ màn hình: x tăng sang phải, y tăng xuống dưới.
 */
public final class Vector2D {
 
    // =========================================================
    // Hằng số
    // =========================================================
 
    public static final Vector2D ZERO  = new Vector2D(0, 0);
    public static final Vector2D UNIT_X = new Vector2D(1, 0);  // → EAST
    public static final Vector2D UNIT_Y = new Vector2D(0, 1);  // ↓ SOUTH
 
    // =========================================================
    // Field
    // =========================================================
 
    private final double x;
    private final double y;
 
    // =========================================================
    // Constructor & Factory
    // =========================================================
 
    public Vector2D(double x, double y) {
        this.x = x;
        this.y = y;
    }
 
    /**
     * Tạo vector từ điểm A đến điểm B: B - A.
     */
    public static Vector2D between(Position from, Position to) {
        return new Vector2D(to.getX() - from.getX(), to.getY() - from.getY());
    }
 
    /**
     * Tạo unit vector từ một góc (degrees).
     */
    public static Vector2D fromAngle(double degrees) {
        double rad = Math.toRadians(degrees);
        return new Vector2D(Math.cos(rad), Math.sin(rad));
    }
 
    // =========================================================
    // Getters
    // =========================================================
 
    public double getX() { return x; }
    public double getY() { return y; }
 
    // =========================================================
    // Phép toán vector cơ bản
    // =========================================================
 
    /** Cộng hai vector: this + other. */
    public Vector2D add(Vector2D other) {
        return new Vector2D(this.x + other.x, this.y + other.y);
    }
 
    /** Trừ hai vector: this - other. */
    public Vector2D subtract(Vector2D other) {
        return new Vector2D(this.x - other.x, this.y - other.y);
    }
 
    /** Nhân vector với scalar: this * scalar. */
    public Vector2D scale(double scalar) {
        return new Vector2D(this.x * scalar, this.y * scalar);
    }
 
    /** Phủ định vector: -this. */
    public Vector2D negate() {
        return new Vector2D(-this.x, -this.y);
    }
 
    // =========================================================
    // Độ lớn và chuẩn hóa
    // =========================================================
 
    /** Độ lớn (magnitude) của vector. */
    public double magnitude() {
        return Math.sqrt(x * x + y * y);
    }
 
    /** Bình phương độ lớn — nhanh hơn magnitude() khi chỉ cần so sánh. */
    public double magnitudeSquared() {
        return x * x + y * y;
    }
 
    /**
     * Trả về unit vector (cùng hướng, độ lớn = 1).
     *
     * @throws ArithmeticException nếu vector là zero vector
     */
    public Vector2D normalize() {
        double mag = magnitude();
        if (mag == 0) throw new ArithmeticException("Cannot normalize zero vector");
        return new Vector2D(x / mag, y / mag);
    }
 
    /** Trả về true nếu đây là zero vector (x = 0, y = 0). */
    public boolean isZero() {
        return x == 0 && y == 0;
    }
 
    // =========================================================
    // Tích vô hướng và tích có hướng
    // =========================================================
 
    /**
     * Tích vô hướng (dot product): this · other.
     *
     * <ul>
     *   <li>> 0 → hai vector cùng chiều (góc < 90°)</li>
     *   <li>= 0 → vuông góc nhau</li>
     *   <li>< 0 → ngược chiều (góc > 90°)</li>
     * </ul>
     *
     * Dùng trong {@code Position.isAheadInDirection()} để kiểm tra
     * một xe có nằm phía trước xe khác không.
     */
    public double dot(Vector2D other) {
        return this.x * other.x + this.y * other.y;
    }
 
    /**
     * Tích có hướng 2D (cross product, trả về scalar — thành phần z):
     * this × other = x1*y2 - y1*x2.
     *
     * <ul>
     *   <li>> 0 → other nằm bên trái this (ngược chiều kim đồng hồ)</li>
     *   <li>< 0 → other nằm bên phải this (thuận chiều kim đồng hồ)</li>
     *   <li>= 0 → hai vector cùng phương</li>
     * </ul>
     *
     * Dùng trong {@code GeometryUtils.segmentsIntersect()} để kiểm tra
     * giao nhau của hai đoạn thẳng.
     */
    public double cross(Vector2D other) {
        return this.x * other.y - this.y * other.x;
    }
 
    // =========================================================
    // Góc
    // =========================================================
 
    /**
     * Góc của vector này so với trục x dương (degrees, trong [-180, 180]).
     */
    public double angleDegrees() {
        return Math.toDegrees(Math.atan2(y, x));
    }
 
    /**
     * Góc giữa hai vector (degrees, trong [0, 180]).
     */
    public double angleBetween(Vector2D other) {
        double cosTheta = this.dot(other) / (this.magnitude() * other.magnitude());
        cosTheta = Math.max(-1.0, Math.min(1.0, cosTheta)); // clamp để tránh NaN
        return Math.toDegrees(Math.acos(cosTheta));
    }
 
    /**
     * Xoay vector thêm {@code degrees} độ (dương = theo chiều kim đồng hồ).
     */
    public Vector2D rotate(double degrees) {
        double rad = Math.toRadians(degrees);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        return new Vector2D(x * cos - y * sin, x * sin + y * cos);
    }
 
    /**
     * Trả về vector pháp tuyến vuông góc (xoay 90° ngược chiều kim đồng hồ).
     * Hữu ích để tính normal của vạch kẻ đường.
     */
    public Vector2D perpendicular() {
        return new Vector2D(-y, x);
    }
 
    // =========================================================
    // Chuyển đổi
    // =========================================================
 
    /**
     * Chuyển vector này thành Direction (góc của vector).
     *
     * @throws IllegalArgumentException nếu là zero vector
     */
    public Direction toDirection() {
        return Direction.fromVector(this);
    }
 
    @Override
    public String toString() {
        return String.format("Vector2D(%.2f, %.2f)", x, y);
    }
 
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Vector2D)) return false;
        Vector2D v = (Vector2D) o;
        return Double.compare(v.x, x) == 0 && Double.compare(v.y, y) == 0;
    }
 
    @Override
    public int hashCode() {
        return 31 * Double.hashCode(x) + Double.hashCode(y);
    }
}
 