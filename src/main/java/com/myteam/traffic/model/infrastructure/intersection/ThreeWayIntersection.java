package com.myteam.traffic.model.infrastructure.intersection;

/**
 * Lớp ThreeWayIntersection đại diện cho các loại ngã ba (nút giao có 3 nhánh).
 * 
 * <p>Ngã ba là một dạng nút giao phổ biến nhưng có các đặc tính hình học khác nhau 
 * (chữ T hoặc chữ Y), ảnh hưởng trực tiếp đến tốc độ rẽ và tầm nhìn của phương tiện.</p>
 */
public class ThreeWayIntersection extends Intersection {

    /**
     * Định nghĩa các kiểu hình dáng cụ thể của ngã ba.
     */
    public enum SubType { 
        /** Ngã ba vuông góc, một đường kết thúc tại một đường thẳng khác. */
        T_SHAPE, 
        /** Ngã ba chia nhánh đều, thường có góc giữa các đường khoảng 120 độ. */
        Y_SHAPE, 
        /** Kiểu ngã ba không xác định hoặc không theo quy chuẩn. */
        UNKNOWN 
    }

    private final SubType subType;

    /**
     * Khởi tạo một ngã ba mặc định (chưa xác định hình dáng).
     * 
     * @param centerX Tọa độ X của tâm nút giao.
     * @param centerY Tọa độ Y của tâm nút giao.
     */
    public ThreeWayIntersection(double centerX, double centerY) {
        this(centerX, centerY, SubType.UNKNOWN);
    }

    /**
     * Khởi tạo một ngã ba với hình dáng cụ thể.
     * 
     * @param centerX Tọa độ X của tâm nút giao.
     * @param centerY Tọa độ Y của tâm nút giao.
     * @param subType Kiểu hình dáng (T_SHAPE, Y_SHAPE, UNKNOWN).
     */
    public ThreeWayIntersection(double centerX, double centerY, SubType subType) {
        super(centerX, centerY);
        this.subType = (subType == null) ? SubType.UNKNOWN : subType;
    }

    /**
     * Trả về số lượng đường kết nối cố định cho ngã ba là 3.
     * 
     * @return Luôn trả về 3.
     */
    @Override
    public int getExpectedRoadCount() {
        return 3;
    }

    /**
     * Trả về tên tiếng Việt tương ứng với hình dáng của ngã ba.
     * 
     * @return Tên mô tả loại ngã ba.
     */
    @Override
    public String getIntersectionType() {
        return switch (subType) {
            case T_SHAPE -> "Ngã ba hình chữ T";
            case Y_SHAPE -> "Ngã ba hình chữ Y";
            default      -> "Ngã ba (Chưa xác định)";
        };
    }

    /**
     * Kiểm tra xem nút giao có yêu cầu góc cua gắt hay không.
     * 
     * <p>Trong ngã ba chữ T (T-SHAPE), các phương tiện rẽ từ nhánh phụ vào đường chính 
     * thường phải thực hiện góc cua gần 90 độ, yêu cầu giảm tốc độ sâu hơn so với ngã ba chữ Y.</p>
     * 
     * @return true nếu là ngã ba chữ T.
     */
    public boolean isSharpTurn() {
        return subType == SubType.T_SHAPE;
    }

    /**
     * Lấy kiểu hình dáng cụ thể của ngã ba.
     */
    public SubType getSubType() {
        return subType;
    }

    /**
     * Trả về chuỗi thông tin phục vụ debug.
     */
    @Override
    public String toString() {
        return String.format("ThreeWayIntersection[Type=%s, Pos=(%.1f, %.1f)]",
                subType, centerX, centerY);
    }
}