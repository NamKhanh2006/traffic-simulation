package com.myteam.traffic.model.infrastructure.intersection;

/**
 * Lớp FiveWayIntersection đại diện cho một ngã năm (nút giao có 5 nhánh).
 * 
 * <p>Tại sao lớp này ngắn? 
 * Vì nó tận dụng cấu trúc của lớp cha Intersection. Thay vì viết lại logic 
 * quản lý danh sách đường hay kiểm tra null, nó chỉ cung cấp các "hằng số" 
 * đặc trưng cho một ngã năm (số nhánh = 5).</p>
 */
public class FiveWayIntersection extends Intersection {

    /**
     * Khởi tạo một ngã năm tại vị trí xác định.
     * 
     * @param centerX Tọa độ X tâm nút giao.
     * @param centerY Tọa độ Y tâm nút giao.
     */
    public FiveWayIntersection(double centerX, double centerY) {
        // super() gọi constructor của Intersection để lưu tọa độ và khởi tạo danh sách
        super(centerX, centerY);
    }

    /**
     * Định nghĩa số lượng nhánh kết nối tối đa cho ngã năm.
     * 
     * <p>Giá trị này sẽ được phương thức connectRoad() ở lớp cha sử dụng 
     * để tự động ngăn chặn việc kết nối nhánh thứ 6 vào nút giao này.</p>
     * 
     * @return Luôn trả về 5.
     */
    @Override
    public int getExpectedRoadCount() {
        return 5;
    }

    /**
     * Trả về tên định danh loại nút giao là Ngã năm (Star-junction).
     * Hữu ích cho việc hiển thị trên giao diện hoặc ghi log hệ thống.
     */
    @Override
    public String getIntersectionType() {
        return "Five-way intersection (Star-junction)";
    }
}