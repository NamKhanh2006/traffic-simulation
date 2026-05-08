package com.myteam.traffic.model.infrastructure;

import java.util.*;

/**
 * Lớp RoadSegment đại diện cho một đoạn đường thẳng trong hệ thống giao thông.
 * 
 * <p>Thiết kế theo nguyên lý <b>Clean OOP</b> và <b>Immutable Object</b>:
 * <ul>
 *     <li>Tất cả các thuộc tính đều là <code>final</code>.</li>
 *     <li>Không cung cấp các phương thức setter để thay đổi dữ liệu hiện có.</li>
 *     <li>Mọi thay đổi về cấu trúc đều tạo ra một đối tượng mới hoàn toàn.</li>
 * </ul>
 * 
 * <p>Lớp này chịu trách nhiệm tính toán hình học (chiều dài, góc quay) và 
 * xác định vị trí chính xác của phương tiện trên từng làn đường dựa vào offset.</p>
 */
public class RoadSegment {

    // ── Hình học (Geometry) ──────────────────────────────────
    private final double startX, startY; // Tọa độ điểm bắt đầu (tim đường)
    private final double endX, endY;     // Tọa độ điểm kết thúc (tim đường)
    private final double length;         // Chiều dài vật lý của đoạn đường
    private final double angle;          // Góc của đoạn đường (Radian) so với trục hoành

    // ── Làn đường (Lanes) ─────────────────────────────────────
    /** Danh sách các làn đường đã được sắp xếp và không thể sửa đổi (unmodifiable) */
    private final List<Lane> lanes;
    
    /** Cache khoảng cách từ tim đường (center line) đến tâm của mỗi làn */
    private final double[] cachedOffsets;
    
    /** Cache hướng di chuyển của từng làn để tối ưu tốc độ truy vấn */
    private final Lane.Direction[] cachedDirections;
    
    /** Mảng ánh xạ nhanh từ ID làn đường (index) sang vị trí trong danh sách lanes */
    private final int[] laneIndexToPos; 

    // ── Constructor ───────────────────────────────────────────

    /**
     * Khởi tạo một đoạn đường mới.
     * Trong quá trình khởi tạo, các thông số hình học và hệ thống cache sẽ được tính toán trước một lần.
     * 
     * @param sx Tọa độ X bắt đầu
     * @param sy Tọa độ Y bắt đầu
     * @param ex Tọa độ X kết thúc
     * @param ey Tọa độ Y kết thúc
     * @param inputLanes Danh sách các làn đường thuộc đoạn đường này
     * @throws IllegalArgumentException Nếu điểm đầu/cuối trùng nhau hoặc danh sách làn rỗng
     */
    public RoadSegment(double sx, double sy, double ex, double ey, List<Lane> inputLanes) {
        if (sx == ex && sy == ey)
            throw new IllegalArgumentException("Điểm đầu và cuối không được trùng nhau");
        if (inputLanes == null || inputLanes.isEmpty())
            throw new IllegalArgumentException("Danh sách làn không được rỗng");

        this.startX = sx; this.startY = sy;
        this.endX   = ex; this.endY   = ey;

        // Tính toán chiều dài và góc quay dựa trên Vector (end - start)
        double dx = ex - sx;
        double dy = ey - sy;
        this.length = Math.sqrt(dx * dx + dy * dy);
        this.angle  = Math.atan2(dy, dx);

        // Sắp xếp làn đường theo thứ tự Index và đóng băng danh sách để đảm bảo tính bất biến
        List<Lane> sorted = new ArrayList<>(inputLanes);
        sorted.sort(Comparator.comparingInt(Lane::getIndex));
        this.lanes = Collections.unmodifiableList(sorted);

        // Khởi tạo các mảng cache để tối ưu hiệu năng tính toán tọa độ xe (tránh duyệt List/Stream liên tục)
        int count = sorted.size();
        this.cachedOffsets    = new double[count];
        this.cachedDirections = new Lane.Direction[count];

        // Tìm Index lớn nhất để khởi tạo mảng Lookup (O(1) truy cập sau này)
        int maxIndex = sorted.stream().mapToInt(Lane::getIndex).max().getAsInt();
        this.laneIndexToPos = new int[maxIndex + 1];
        Arrays.fill(laneIndexToPos, -1);

        // Tính toán Offset (khoảng cách lệch so với tim đường) cho từng làn
        // Giả định: Tim đường nằm ở giữa tổng độ rộng của tất cả các làn
        double totalWidth = sorted.stream().mapToDouble(Lane::getWidth).sum();
        double currentOffset = -totalWidth / 2.0;

        for (int i = 0; i < count; i++) {
            Lane l = sorted.get(i);
            // Offset của làn i là khoảng cách từ tim đường đến tâm của làn đó
            cachedOffsets[i]             = currentOffset + (l.getWidth() / 2.0);
            cachedDirections[i]          = l.getDirection();
            laneIndexToPos[l.getIndex()] = i;
            currentOffset += l.getWidth();
        }
    }

    // ── Truy vấn vị trí (Positioning) ─────────────────────────

    /**
     * Tính toán vị trí chính xác của một phương tiện trong không gian 2D.
     * 
     * @param targetLaneId ID (index) của làn đường phương tiện đang đi
     * @param t Tỉ lệ tiến độ trên đường [0.0 = đầu đường, 1.0 = cuối đường]
     * @return Mảng double gồm: [tọa độ X, tọa độ Y, góc xoay độ Degrees]
     * @throws IllegalArgumentException Nếu ID làn đường không tồn tại trong segment này
     */
    public double[] getPositionOnLane(int targetLaneId, double t) {
        if (targetLaneId < 0
                || targetLaneId >= laneIndexToPos.length
                || laneIndexToPos[targetLaneId] == -1) {
            throw new IllegalArgumentException("Không tìm thấy làn có ID: " + targetLaneId);
        }

        int pos            = laneIndexToPos[targetLaneId];
        double offset      = cachedOffsets[pos];
        Lane.Direction dir = cachedDirections[pos];

        // Tính góc vuông (perpendicular) để dịch chuyển từ tim đường ra tâm làn
        double perpAngle = angle + Math.PI / 2.0;
        
        // Điều chỉnh t và hướng xoay dựa trên hướng di chuyển của làn (Thuận/Ngược)
        double actualT   = (dir == Lane.Direction.BACKWARD) ? (1.0 - t) : t;
        double rotation  = (dir == Lane.Direction.BACKWARD) ? (angle + Math.PI) : angle;

        // 1. Tính tọa độ điểm trên đường trung tâm (center line) dựa vào tỉ lệ t
        double cx = startX + actualT * (endX - startX);
        double cy = startY + actualT * (endY - startY);

        // 2. Tịnh tiến tọa độ (cx, cy) theo góc vuông một khoảng bằng offset
        return new double[]{
                cx + offset * Math.cos(perpAngle),
                cy + offset * Math.sin(perpAngle),
                Math.toDegrees(rotation)
        };
    }

    // ── "Setters" theo kiểu Immutable (Wither methods) ─────────

    /** 
     * Thay đổi tọa độ đoạn đường.
     * @return Một đối tượng RoadSegment mới với tọa độ mới nhưng giữ nguyên các làn đường.
     */
    public RoadSegment withNewPoints(double sx, double sy, double ex, double ey) {
        return new RoadSegment(sx, sy, ex, ey, this.lanes);
    }

    /** 
     * Thay đổi danh sách làn đường.
     * @return Một đối tượng RoadSegment mới với danh sách làn mới trên cùng tọa độ cũ.
     */
    public RoadSegment withNewLanes(List<Lane> newLanes) {
        return new RoadSegment(startX, startY, endX, endY, newLanes);
    }

    // ── Getters ───────────────────────────────────────────────

    public double getStartX() { return startX; }
    public double getStartY() { return startY; }
    public double getEndX()   { return endX; }
    public double getEndY()   { return endY; }
    public double getLength() { return length; }
    public double getAngle()  { return angle; }

    /** 
     * Trả về danh sách làn đường. 
     * Vì danh sách đã được bảo vệ bởi Collections.unmodifiableList, việc trả trực tiếp vẫn đảm bảo an toàn.
     */
    public List<Lane> getLanes() { return lanes; }
}