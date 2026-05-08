package com.myteam.traffic.model.infrastructure.intersection;

/**
 * HighwayRamp đại diện cho điểm nhập làn hoặc tách làn trên đường cao tốc.
 * 
 * <p>Đây là vị trí kết nối giữa đường cao tốc chính và đường dẫn (ramp),
 * cho phép xe đi vào hoặc rời khỏi cao tốc.</p>
 * 
 * <p>Thông thường nút này gồm:
 * <ul>
 *   <li>2 đoạn của tuyến cao tốc chính</li>
 *   <li>1 đoạn đường dẫn lên/xuống cao tốc</li>
 * </ul>
 * </p>
 */
public class HighwayRamp extends Intersection {

    /**
     * Tạo một nút nhập/tách làn trên cao tốc.
     *
     * @param centerX tọa độ X của nút giao
     * @param centerY tọa độ Y của nút giao
     */
    public HighwayRamp(double centerX, double centerY) {
        super(centerX, centerY);
    }

    /**
     * Trả về số lượng đường thường kết nối với nút này.
     *
     * @return số lượng đường kết nối dự kiến
     */
    @Override
    public int getExpectedRoadCount() {
        // Gồm:
        // - 2 đoạn của đường cao tốc chính
        // - 1 đoạn đường dẫn (ramp)
        return 3;
    }

    /**
     * Trả về tên loại nút giao.
     *
     * @return chuỗi mô tả loại nút giao
     */
    @Override
    public String getIntersectionType() {
        return "Highway Ramp / Interchange";
    }
}