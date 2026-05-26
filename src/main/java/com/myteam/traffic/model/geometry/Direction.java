package com.myteam.traffic.model.geometry;


/**
* Hướng di chuyển, được biểu diễn bằng góc (degrees) trong hệ tọa độ màn hình:
*
* <pre>
*        270° (UP / NORTH)
*             │
*  180° ──────┼────── 0° / 360° (RIGHT / EAST)
*  (LEFT)     │
*            90° (DOWN / SOUTH)
* </pre>
*
* Góc luôn được chuẩn hóa về [0, 360).
* Dùng góc thực (double) thay vì enum NORTH/SOUTH/... vì xe di chuyển
* mọi góc độ trong simulation, không chỉ 4 hướng cơ bản.
*/
public final class Direction {

   // =========================================================
   // Hằng số các hướng phổ biến
   // =========================================================

   public static final Direction EAST  = new Direction(0);
   public static final Direction SOUTH = new Direction(90);
   public static final Direction WEST  = new Direction(180);
   public static final Direction NORTH = new Direction(270);

   // =========================================================
   // Field
   // =========================================================

   /** Góc tính bằng degrees, đã chuẩn hóa về [0, 360). */
   private final double degrees;

   // =========================================================
   // Constructor & Factory
   // =========================================================

   /**
    * Tạo Direction từ góc degrees (tự động chuẩn hóa về [0, 360)).
    */
   public Direction(double degrees) {
       this.degrees = normalize(degrees);
   }

   /**
    * Tạo Direction từ một Vector2D chỉ hướng.
    * Ví dụ: Vector2D(1, 0) → 0° (EAST), Vector2D(0, 1) → 90° (SOUTH).
    *
    * @throws IllegalArgumentException nếu vector là zero vector
    */
   public static Direction fromVector(Vector2D v) {
       if (v.isZero()) throw new IllegalArgumentException("Cannot get direction from zero vector");
       double rad = Math.atan2(v.getY(), v.getX());
       return new Direction(Math.toDegrees(rad));
   }

   /**
    * Tạo Direction từ góc radians.
    */
   public static Direction fromRadians(double radians) {
       return new Direction(Math.toDegrees(radians));
   }

   // =========================================================
   // Chuyển đổi
   // =========================================================

   public double toDegrees() { return degrees; }

   public double toRadians() { return Math.toRadians(degrees); }

   /**
    * Trả về vector đơn vị (unit vector) tương ứng với hướng này.
    * Hữu ích khi cần tính vị trí sau khi di chuyển.
    */
   public Vector2D toUnitVector() {
       double rad = toRadians();
       return new Vector2D(Math.cos(rad), Math.sin(rad));
   }

   // =========================================================
   // Phép toán trên hướng
   // =========================================================

   /**
    * Xoay hướng thêm {@code deltaDegrees} độ (dương = theo chiều kim đồng hồ).
    */
   public Direction rotate(double deltaDegrees) {
       return new Direction(this.degrees + deltaDegrees);
   }

   /**
    * Trả về hướng ngược lại (opposite), cách 180°.
    */
   public Direction opposite() {
       return new Direction(this.degrees + 180);
   }

   /**
    * Tính góc lệch (signed) từ hướng này đến {@code other},
    * kết quả nằm trong (-180, 180].
    * Dương = xoay theo chiều kim đồng hồ.
    */
   public double angleTo(Direction other) {
       double delta = other.degrees - this.degrees;
       if (delta > 180)  delta -= 360;
       if (delta <= -180) delta += 360;
       return delta;
   }

   /**
    * Trả về true nếu {@code other} nằm trong góc {@code toleranceDegrees}
    * so với hướng này. Hữu ích để kiểm tra "xe có đang đi cùng hướng không".
    */
   public boolean isApproximately(Direction other, double toleranceDegrees) {
       return Math.abs(angleTo(other)) <= toleranceDegrees;
   }

   // =========================================================
   // Utility
   // =========================================================

   /** Chuẩn hóa góc về [0, 360). */
   private static double normalize(double deg) {
       deg = deg % 360;
       if (deg < 0) deg += 360;
       return deg;
   }

   @Override
   public String toString() {
       return String.format("Direction(%.1f°)", degrees);
   }

   @Override
   public boolean equals(Object o) {
       if (this == o) return true;
       if (!(o instanceof Direction)) return false;
       return Double.compare(((Direction) o).degrees, degrees) == 0;
   }

   @Override
   public int hashCode() {
       return Double.hashCode(degrees);
   }
}
