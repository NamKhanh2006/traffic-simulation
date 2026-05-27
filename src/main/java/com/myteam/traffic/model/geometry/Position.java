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
    
    
}