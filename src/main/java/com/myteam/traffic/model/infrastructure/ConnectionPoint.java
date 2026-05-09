package com.myteam.traffic.model.infrastructure;

/**
 * Mô tả cách một RoadSegment gắn vào một Intersection.
 *
 * UI cần biết 2 thứ để vẽ đúng:
 *   1. Đầu nào của segment chạm vào nút giao (START hay END)?
 *   2. Góc tiếp cận từ hướng nào (Bắc/Nam/Đông/Tây)?
 *
 * Immutable — tạo một lần, không đổi.
 */
public final class ConnectionPoint {

    /** Đầu nào của RoadSegment gắn vào Intersection. */
    public enum End { START, END }

    private final RoadSegment segment;
    private final End         end;

    /**
     * Góc tiếp cận của segment tại điểm kết nối (radian, tính từ Intersection nhìn ra).
     * Ví dụ: segment đến từ phía Bắc → approachAngle ≈ -PI/2
     * Tính tự động từ tọa độ, không cần truyền thủ công.
     */
    private final double approachAngle;

    public ConnectionPoint(RoadSegment segment, End end) {
        if (segment == null) throw new IllegalArgumentException("segment không được null");
        if (end == null)     throw new IllegalArgumentException("end không được null");
        this.segment = segment;
        this.end     = end;
        // Tính góc: hướng từ điểm kết nối ra ngoài (nhìn từ nút giao)
        this.approachAngle = computeApproachAngle(segment, end);
    }

    private static double computeApproachAngle(RoadSegment seg, End end) {
        if (end == End.START) {
            // Segment đi ra từ START → hướng nhìn từ nút giao là ngược chiều segment
            return Math.atan2(seg.getStartY() - seg.getEndY(),
                              seg.getStartX() - seg.getEndX());
        } else {
            // Segment đi vào END → hướng nhìn từ nút giao là chiều segment
            return Math.atan2(seg.getEndY() - seg.getStartY(),
                              seg.getEndX() - seg.getStartX());
        }
    }

    /** Tọa độ X điểm chạm vào Intersection. */
    public double getX() {
        return (end == End.START) ? segment.getStartX() : segment.getEndX();
    }

    /** Tọa độ Y điểm chạm vào Intersection. */
    public double getY() {
        return (end == End.START) ? segment.getStartY() : segment.getEndY();
    }

    public RoadSegment getSegment()     { return segment;      }
    public End         getEnd()         { return end;          }
    public double      getApproachAngle() { return approachAngle; }

    @Override
    public String toString() {
        return String.format("ConnectionPoint[seg=%s, end=%s, angle=%.1f°]",
                segment.getClass().getSimpleName(), end,
                Math.toDegrees(approachAngle));
    }
}