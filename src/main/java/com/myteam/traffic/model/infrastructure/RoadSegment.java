package com.myteam.traffic.model.infrastructure;

import java.util.*;

public class RoadSegment {

    private final double startX, startY;
    private final double endX, endY;
    private final double length;
    private final double angle;
    private boolean onRamp;
    private boolean connector;

    private final List<Lane> lanes;
    private final double[] cachedOffsets;
    private final Lane.Direction[] cachedDirections;
    private final int[] laneIndexToPos;

    // Hệ số đồng bộ kích thước vật lý với kích thước hiển thị UI (20 pixel = 3.5m)
    private static final double UI_SCALE = 20.0 / 3.5;

    public RoadSegment(double sx, double sy, double ex, double ey, List<Lane> inputLanes) {
        if (sx == ex && sy == ey)
            throw new IllegalArgumentException("Điểm đầu và cuối không được trùng nhau");
        if (inputLanes == null || inputLanes.isEmpty())
            throw new IllegalArgumentException("Danh sách làn không được rỗng");

        this.startX = sx; this.startY = sy;
        this.endX   = ex; this.endY   = ey;

        double dx = ex - sx;
        double dy = ey - sy;
        this.length = Math.sqrt(dx * dx + dy * dy);
        this.angle  = Math.atan2(dy, dx);

        List<Lane> sorted = new ArrayList<>(inputLanes);
        sorted.sort(Comparator.comparingInt(Lane::getIndex));
        this.lanes = Collections.unmodifiableList(sorted);

        int count = sorted.size();
        this.cachedOffsets    = new double[count];
        this.cachedDirections = new Lane.Direction[count];

        int maxIndex = sorted.stream().mapToInt(Lane::getIndex).max().getAsInt();
        this.laneIndexToPos = new int[maxIndex + 1];
        Arrays.fill(laneIndexToPos, -1);

        double totalWidth = sorted.stream().mapToDouble(l -> l.getWidth() * UI_SCALE).sum();
        double currentOffset = -totalWidth / 2.0;

        for (int i = 0; i < count; i++) {
            Lane l = sorted.get(i);
            double scaledWidth = l.getWidth() * UI_SCALE;

            cachedOffsets[i]             = currentOffset + (scaledWidth / 2.0);
            cachedDirections[i]          = l.getDirection();
            laneIndexToPos[l.getIndex()] = i;

            currentOffset += scaledWidth;
        }
    }

    public double[] getPositionOnLane(int targetLaneId, double t) {
        if (targetLaneId < 0 || targetLaneId >= laneIndexToPos.length || laneIndexToPos[targetLaneId] == -1) {
            throw new IllegalArgumentException("Không tìm thấy làn có ID: " + targetLaneId);
        }

        int pos            = laneIndexToPos[targetLaneId];
        double offset      = cachedOffsets[pos];
        Lane.Direction dir = cachedDirections[pos];

        double perpAngle = angle + Math.PI / 2.0;

        // FIX LỖI ĐI LÙI: KHÔNG ĐƯỢC đảo ngược biến t nữa!
        // Vì TrafficController đã tự trừ lùi t đối với làn BACKWARD rồi.
        double rotation  = (dir == Lane.Direction.BACKWARD) ? (angle + Math.PI) : angle;

        // Dùng trực tiếp t để tính nội suy tọa độ
        double cx = startX + t * (endX - startX);
        double cy = startY + t * (endY - startY);

        return new double[]{
                cx + offset * Math.cos(perpAngle),
                cy + offset * Math.sin(perpAngle),
                Math.toDegrees(rotation)
        };
    }

    public RoadSegment withNewPoints(double sx, double sy, double ex, double ey) {
        return new RoadSegment(sx, sy, ex, ey, this.lanes);
    }

    public RoadSegment withNewLanes(List<Lane> newLanes) {
        return new RoadSegment(startX, startY, endX, endY, newLanes);
    }

    public double getStartX() { return startX; }
    public double getStartY() { return startY; }
    public double getEndX()   { return endX; }
    public double getEndY()   { return endY; }
    public double getLength() { return length; }
    public double getAngle()  { return angle; }
    public boolean isOnRamp() { return onRamp; }
    public void setOnRamp(boolean onRamp) { this.onRamp = onRamp; }

    public boolean isConnector() { return connector; }
    public void setConnector(boolean connector) { this.connector = connector; }
    public int getLaneCount() { return lanes.size(); }
    public List<Lane> getLanes() { return lanes; }
    public double getSpeedLimit(){ return 90.0; }
}