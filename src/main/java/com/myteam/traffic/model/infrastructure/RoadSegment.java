package com.myteam.traffic.model.infrastructure;

import java.util.*;

/**
 * Phiên bản "Clean OOP" - Không dùng đa luồng.
 * Object bất biến (Immutable): mọi trường đều final, không có setter.
 * Muốn thay đổi → tạo object mới qua withNewPoints() hoặc withNewLanes().
 */
public class RoadSegment {

    // ── Hình học ──────────────────────────────────────────────
    private final double startX, startY, endX, endY;
    private final double length;
    private final double angle;

    // ── Làn đường ─────────────────────────────────────────────
    private final List<Lane> lanes;          // unmodifiable
    private final double[]         cachedOffsets;
    private final Lane.Direction[] cachedDirections;
    private final int[]            laneIndexToPos;  // -1 = không tồn tại

    // ── Constructor ───────────────────────────────────────────

    public RoadSegment(double sx, double sy, double ex, double ey, List<Lane> inputLanes) {
        if (sx == ex && sy == ey)
            throw new IllegalArgumentException("Điểm đầu và cuối không được trùng nhau");
        if (inputLanes == null || inputLanes.isEmpty())
            throw new IllegalArgumentException("Danh sách làn không được rỗng");

        this.startX = sx; this.startY = sy;
        this.endX   = ex; this.endY   = ey;

        // Hình học
        double dx = ex - sx;
        double dy = ey - sy;
        this.length = Math.sqrt(dx * dx + dy * dy);
        this.angle  = Math.atan2(dy, dx);

        // Sắp xếp và đóng băng danh sách làn
        List<Lane> sorted = new ArrayList<>(inputLanes);
        sorted.sort(Comparator.comparingInt(Lane::getIndex));
        this.lanes = Collections.unmodifiableList(sorted);

        // Khởi tạo mảng lookup và cache offset/direction
        int count = sorted.size();
        this.cachedOffsets    = new double[count];
        this.cachedDirections = new Lane.Direction[count];

        int maxIndex = sorted.stream().mapToInt(Lane::getIndex).max().getAsInt();
        this.laneIndexToPos = new int[maxIndex + 1];
        Arrays.fill(laneIndexToPos, -1);

        double totalWidth = sorted.stream().mapToDouble(Lane::getWidth).sum();
        double currentOffset = -totalWidth / 2.0;

        for (int i = 0; i < count; i++) {
            Lane l = sorted.get(i);
            cachedOffsets[i]             = currentOffset + (l.getWidth() / 2.0);
            cachedDirections[i]          = l.getDirection();
            laneIndexToPos[l.getIndex()] = i;
            currentOffset += l.getWidth();
        }
    }

    // ── Truy vấn vị trí ───────────────────────────────────────

    /**
     * Trả về tọa độ và góc xoay của phương tiện trên một làn đường.
     *
     * @param targetLaneId Lane.getIndex() của làn cần truy vấn
     * @param t            Tỉ lệ hoàn thành [0.0 – 1.0]
     * @return             [x, y, rotationInDegrees]
     * @throws IllegalArgumentException nếu không tìm thấy làn
     */
    public double[] getPositionOnLane(int targetLaneId, double t) {
        if (targetLaneId < 0
                || targetLaneId >= laneIndexToPos.length
                || laneIndexToPos[targetLaneId] == -1) {
            throw new IllegalArgumentException("Không tìm thấy làn có ID: " + targetLaneId);
        }

        int pos           = laneIndexToPos[targetLaneId];
        double offset     = cachedOffsets[pos];
        Lane.Direction dir = cachedDirections[pos];

        double perpAngle = angle + Math.PI / 2.0;
        double actualT   = (dir == Lane.Direction.BACKWARD) ? (1.0 - t) : t;
        double rotation  = (dir == Lane.Direction.BACKWARD) ? (angle + Math.PI) : angle;

        // FIX: cy phải dùng (endY - startY), không phải (endX - startX)
        double cx = startX + actualT * (endX - startX);
        double cy = startY + actualT * (endY - startY);

        return new double[]{
                cx + offset * Math.cos(perpAngle),
                cy + offset * Math.sin(perpAngle),
                Math.toDegrees(rotation)
        };
    }

    // ── "Setters" theo kiểu Immutable ─────────────────────────

    /** Tạo RoadSegment mới với điểm đầu/cuối khác, giữ nguyên danh sách làn. */
    public RoadSegment withNewPoints(double sx, double sy, double ex, double ey) {
        return new RoadSegment(sx, sy, ex, ey, this.lanes);
    }

    /** Tạo RoadSegment mới với danh sách làn khác, giữ nguyên tọa độ. */
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

    /** Danh sách đã unmodifiable — trả thẳng không cần copy thêm. */
    public List<Lane> getLanes() { return lanes; }
}