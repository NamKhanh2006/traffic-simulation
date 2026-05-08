package com.myteam.traffic.model.infrastructure;

import java.util.List;

/**
 * Lớp HighwaySegment đại diện cho một đoạn đường cao tốc.
 * 
 * <p>Được kế thừa từ {@link RoadSegment}, lớp này bổ sung các quy tắc đặc thù của cao tốc:
 * <ul>
 *     <li>Có giới hạn tốc độ tối thiểu (minSpeedLimit).</li>
 *     <li>Quy định số lượng làn đường tối thiểu (2 hoặc 3 làn).</li>
 *     <li>Hỗ trợ nhận diện làn dừng khẩn cấp (Emergency Lane).</li>
 * </ul>
 */
public class HighwaySegment extends RoadSegment {
    
    private final double minSpeedLimit;    // Tốc độ tối thiểu cho phép (VD: 60km/h)
    private final boolean hasEmergencyLane; // Trạng thái có làn dừng khẩn cấp hay không

    /**
     * Khởi tạo một đoạn đường cao tốc.
     * 
     * @param sx Tọa độ X bắt đầu
     * @param sy Tọa độ Y bắt đầu
     * @param ex Tọa độ X kết thúc
     * @param ey Tọa độ Y kết thúc
     * @param inputLanes Danh sách các làn đường
     * @param minSpeedLimit Tốc độ tối thiểu (phải >= 0)
     * @param hasEmergencyLane Đánh dấu nếu có làn dừng khẩn cấp (thường là làn ngoài cùng bên phải)
     * 
     * @throws IllegalArgumentException Nếu số lượng làn không đủ hoặc tốc độ tối thiểu âm.
     */
    public HighwaySegment(double sx, double sy, double ex, double ey,
                          List<Lane> inputLanes, double minSpeedLimit, boolean hasEmergencyLane) {
        // Gọi Constructor của lớp cha RoadSegment để xử lý hình học và sắp xếp làn
        super(sx, sy, ex, ey, inputLanes);

        // Kiểm tra quy tắc cao tốc: 
        // Nếu có làn khẩn cấp, cần ít nhất 3 làn (2 làn chạy + 1 làn dừng). 
        // Nếu không, cần ít nhất 2 làn.
        int requiredLanes = hasEmergencyLane ? 3 : 2;
        if (inputLanes.size() < requiredLanes) {
            throw new IllegalArgumentException("Cao tốc yêu cầu tối thiểu " + requiredLanes + " làn.");
        }

        // FIX #4: Đảm bảo tốc độ tối thiểu không âm để tránh lỗi logic mô phỏng
        if (minSpeedLimit < 0) {
            throw new IllegalArgumentException("Tốc độ tối thiểu không được âm: " + minSpeedLimit);
        }
        
        this.minSpeedLimit = minSpeedLimit;
        this.hasEmergencyLane = hasEmergencyLane;
    }

    /**
     * Kiểm tra xem một phương tiện có đang chạy dưới tốc độ tối thiểu quy định hay không.
     * 
     * @param currentSpeed Tốc độ hiện tại của phương tiện
     * @return true nếu phương tiện chạy quá chậm so với quy định của cao tốc
     */
    public boolean isBelowMinSpeed(double currentSpeed) {
        return currentSpeed < minSpeedLimit;
    }

    /**
     * Xác định xem một làn đường cụ thể có phải là làn dừng khẩn cấp hay không.
     * Giả định: Làn dừng khẩn cấp luôn là làn có Index lớn nhất (nằm ngoài cùng bên phải).
     * 
     * @param laneId ID của làn cần kiểm tra (Lane.getIndex())
     * @return true nếu đó là làn khẩn cấp
     */
    public boolean isEmergencyLane(int laneId) {
        if (!hasEmergencyLane) return false;
        
        // Lấy làn cuối cùng trong danh sách đã sắp xếp của lớp cha
        return laneId == getLanes().get(getLanes().size() - 1).getIndex();
    }

    // ── Getters ───────────────────────────────────────────────

    /** Trả về giới hạn tốc độ tối thiểu của đoạn cao tốc. */
    public double getMinSpeedLimit() { return minSpeedLimit; }

    /** Kiểm tra xem đoạn cao tốc này có thiết kế làn dừng khẩn cấp không. */
    public boolean hasEmergencyLane() { return hasEmergencyLane; }

    /**
     * Ghi đè phương thức tạo đối tượng mới khi thay đổi tọa độ (Wither method).
     * Đảm bảo tính Immutable nhưng vẫn giữ nguyên các thuộc tính đặc thù của Highway.
     * 
     * @return Một đối tượng HighwaySegment mới với tọa độ được cập nhật.
     */
    @Override
    public HighwaySegment withNewPoints(double sx, double sy, double ex, double ey) {
        return new HighwaySegment(sx, sy, ex, ey, getLanes(), minSpeedLimit, hasEmergencyLane);
    }
}