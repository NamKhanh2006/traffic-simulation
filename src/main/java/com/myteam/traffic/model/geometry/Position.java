package com.myteam.traffic.model.geometry;

public class Position {
    private final double x, y;

    public Position(double x, double y) {
        this.x = x;
        this.y = y;
    }
    
    // =========================================================
    // Getters and setters
    // =========================================================
    public double getX() {
		return x;
	}

	public double getY() {
		return y;
	}

	/** Tính vị trí mới sau khi di chuyển displacement theo direction */
    public Position project(Direction direction, double displacement) {
        double rad = direction.toRadians();
        return new Position(
            x + displacement * Math.cos(rad),
            y + displacement * Math.sin(rad)
        );
    }

    /** Khoảng cách Euclidean đến vị trí khác */
    public double distanceTo(Position other) {
        double dx = this.x - other.x;
        double dy = this.y - other.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * Kiểm tra other có nằm phía trước this theo direction không.
     * Dùng dot product: nếu > 0 thì other ở cùng chiều với direction.
     */
    public boolean isAheadInDirection(Direction direction, Position other) {
        double rad = direction.toRadians();
        double dx = other.x - this.x;
        double dy = other.y - this.y;
        // Dot product với vector đơn vị của direction
        return (dx * Math.cos(rad) + dy * Math.sin(rad)) > 0;
    }
    
    // Thêm vào class Position (com.myteam.traffic.model.geometry.Position)

    /**
     * Tính khoảng cách dọc theo hướng {@code direction} từ điểm này đến điểm {@code other}.
     * Khác với {@link #distanceTo(Position)} (khoảng cách Euclid), method này chỉ tính
     * hình chiếu của vector (this → other) lên vector đơn vị của direction.
     * 
     * <p>Hữu ích để tính khoảng cách giữa hai xe cùng chạy trên một làn đường thẳng,
     * vì nó bỏ qua khoảng cách lệch sang ngang (do xe có thể hơi lệch làn).</p>
     * 
     * <pre>
     * Ví dụ: this = (0,0), other = (10, 1), direction = EAST (0°)
     *   → distanceAlongDirection = 10.0 (bỏ qua độ lệch 1 đơn vị theo Y)
     * </pre>
     *
     * @param direction Hướng di chuyển (đã được chuẩn hóa, không cần unit vector)
     * @param other Điểm đích
     * @return Khoảng cách dọc theo hướng (có thể âm nếu other ở phía sau)
     */
    public double distanceAlongDirection(Direction direction, Position other) {
        double dx = other.x - this.x;
        double dy = other.y - this.y;
        double rad = direction.toRadians();
        // Dot product với unit vector của direction
        return dx * Math.cos(rad) + dy * Math.sin(rad);
    }
    
}