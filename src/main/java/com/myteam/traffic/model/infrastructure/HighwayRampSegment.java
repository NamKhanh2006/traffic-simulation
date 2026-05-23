package com.myteam.traffic.model.infrastructure;

import java.util.List;

/**
 * Lớp HighwayRampSegment đại diện cho các đoạn đường dẫn (ramps) nối giữa đường đô thị và cao tốc.
 * 
 * <p>Về mặt hình học, đây là một {@link RoadSegment} vì nó có quỹ đạo thẳng hoặc cong dài,
 * nhưng về mặt chức năng, nó đóng vai trò là "vùng đệm" tốc độ. Xe lưu thông trên Ramp 
 * phải thay đổi trạng thái động lực học để phù hợp với môi trường cao tốc hoặc đô thị.</p>
 * 
 * <p>Đặc điểm chính:
 * <ul>
 *   <li><b>ONRAMP:</b> Lối vào. Xe tăng tốc từ tốc độ đô thị lên tốc độ cao tốc để nhập làn an toàn.</li>
 *   <li><b>OFFRAMP:</b> Lối ra. Xe giảm tốc từ tốc độ cao tốc xuống tốc độ đô thị trước khi vào nút giao.</li>
 * </ul>
 * </p>
 */
public class HighwayRampSegment extends RoadSegment {

    /** Phân loại mục đích sử dụng của đường dẫn. */
    public enum RampType {
        /** Lối vào cao tốc (đường thường → cao tốc). */
        ONRAMP, 
        /** Lối ra cao tốc (cao tốc → đường thường). */
        OFFRAMP  
    }

    private final RampType rampType;
    private final HighwaySegment connectedHighway; // Tham chiếu đến trục cao tốc chính liên quan

    /**
     * Khởi tạo một đoạn đường dẫn cao tốc.
     * 
     * @param sx Tọa độ X bắt đầu.
     * @param sy Tọa độ Y bắt đầu.
     * @param ex Tọa độ X kết thúc.
     * @param ey Tọa độ Y kết thúc.
     * @param inputLanes Danh sách các làn đường của ramp (thường chỉ từ 1-2 làn).
     * @param rampType Loại dẫn vào hoặc dẫn ra.
     * @param connectedHighway Đối tượng cao tốc mà đoạn dẫn này kết nối tới/lui.
     * @throws IllegalArgumentException Nếu rampType hoặc connectedHighway bị null.
     */
    public HighwayRampSegment(double sx, double sy, double ex, double ey,
                              List<Lane> inputLanes,
                              RampType rampType, HighwaySegment connectedHighway) {
        super(sx, sy, ex, ey, inputLanes);
        
        if (rampType == null) throw new IllegalArgumentException("RampType không được null");
        if (connectedHighway == null) throw new IllegalArgumentException("HighwaySegment không được null");
        
        this.rampType = rampType;
        this.connectedHighway = connectedHighway;
    }

    // ── Getters ──────────────────────────────────────────────

    /** Lấy loại đường dẫn (ONRAMP/OFFRAMP) để điều chỉnh hành vi AI. */
    public RampType getRampType() { 
        return rampType; 
    }

    /** Trả về tham chiếu đến trục cao tốc chính mà đoạn này kết nối. */
    public HighwaySegment getConnectedHighway() { 
        return connectedHighway; 
    }

    /**
     * Phương thức tạo bản sao với tọa độ mới (Wither pattern).
     * 
     * <p>Hữu ích khi bạn cần thay đổi vị trí hình học của đường dẫn trên bản đồ 
     * mà không muốn thay đổi các thiết lập về làn đường hay tham chiếu cao tốc.</p>
     * 
     * @return Một đối tượng HighwayRampSegment mới với tọa độ được cập nhật.
     */
    @Override
    public HighwayRampSegment withNewPoints(double sx, double sy, double ex, double ey) {
        return new HighwayRampSegment(sx, sy, ex, ey, getLanes(), rampType, connectedHighway);
    }
}