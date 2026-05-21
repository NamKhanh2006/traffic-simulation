package com.myteam.traffic.model.infrastructure.intersection;

import com.myteam.traffic.model.infrastructure.ConnectionPoint;
import com.myteam.traffic.model.infrastructure.IntersectionRenderData;
import com.myteam.traffic.model.infrastructure.IntersectionRenderData.ArmData;
import com.myteam.traffic.model.infrastructure.RoadSegment;
import com.myteam.traffic.model.infrastructure.Lane;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class Intersection {

    private final double centerX;
    private final double centerY;
    private final List<ConnectionPoint> connections = new ArrayList<>();

    public Intersection(double centerX, double centerY) {
        this.centerX = centerX;
        this.centerY = centerY;
    }

    public abstract int    getExpectedRoadCount();
    public abstract String getIntersectionType();

    public void connectRoad(ConnectionPoint cp) {
        if (cp == null) throw new IllegalArgumentException("ConnectionPoint không được null");
        if (connections.size() >= getExpectedRoadCount())
            throw new IllegalStateException(String.format(
                    "Vượt quá số đường kết nối cho phép (%d) tại %s (%.1f, %.1f)",
                    getExpectedRoadCount(), getIntersectionType(), centerX, centerY));
        connections.add(cp);
    }

    public void connectRoad(RoadSegment road, ConnectionPoint.End end) {
        connectRoad(new ConnectionPoint(road, end));
    }

    public void disconnectRoad(RoadSegment road) {
        connections.removeIf(cp -> cp.getSegment() == road);
    }

    public boolean replaceRoad(RoadSegment oldRoad, RoadSegment newRoad) {
        for (int i = 0; i < connections.size(); i++) {
            if (connections.get(i).getSegment() == oldRoad) {
                ConnectionPoint old = connections.get(i);
                connections.set(i, new ConnectionPoint(newRoad, old.getEnd()));
                return true;
            }
        }
        return false;
    }

    public List<RoadSegment> getConnectedRoads() {
        List<RoadSegment> list = new ArrayList<>();
        for (ConnectionPoint cp : connections) list.add(cp.getSegment());
        return Collections.unmodifiableList(list);
    }

    public List<ConnectionPoint> getConnections() {
        return Collections.unmodifiableList(connections);
    }

    public int    getRoadCount() { return connections.size(); }
    public double getCenterX()   { return centerX; }
    public double getCenterY()   { return centerY; }

    public IntersectionRenderData getRenderData() {
        List<ArmData> arms = new ArrayList<>();
        double maxHalfWidth      = 0;
        double totalWidthAllArms = 0;

        for (ConnectionPoint cp : connections) {
            RoadSegment seg = cp.getSegment();
            double totalWidth = seg.getLanes().stream()
                    .mapToDouble(l -> (l.getWidth() / 3.5) * 20.0).sum();
            maxHalfWidth      = Math.max(maxHalfWidth, totalWidth / 2.0);
            totalWidthAllArms += totalWidth;
            arms.add(new ArmData(cp.getX(), cp.getY(),
                    Math.toDegrees(cp.getApproachAngle()),
                    totalWidth, seg.getLanes().size(), seg));
        }

        double radius;

        if (getIntersectionType().contains("Vòng xuyến")) {
            radius = Math.max(55, totalWidthAllArms / (Math.PI * 1.1));
        } else if (arms.isEmpty()) {
            radius = 30;
        } else {
            // Góc nhỏ nhất giữa 2 nhánh → radius cần thiết để tròn tâm lấp kín
            double minSinHalfAngle = 1.0;
            if (arms.size() >= 2) {
                List<Double> angles = new ArrayList<>();
                for (ArmData a : arms) angles.add(a.approachAngleDeg);
                Collections.sort(angles);
                for (int i = 0; i < angles.size(); i++) {
                    double gap = angles.get((i + 1) % angles.size()) - angles.get(i);
                    if (gap < 0) gap += 360;
                    double s = Math.abs(Math.sin(Math.toRadians(gap / 2)));
                    if (s > 0.05) minSinHalfAngle = Math.min(minSinHalfAngle, s);
                }
            }
            radius = Math.max(25, maxHalfWidth / minSinHalfAngle);

            // Giới hạn trên: không quá 35% độ dài nhánh ngắn nhất
            double minArmLen = arms.stream()
                    .mapToDouble(a -> a.segment.getLength())
                    .filter(l -> l > 1).min().orElse(Double.MAX_VALUE);
            if (minArmLen < Double.MAX_VALUE)
                radius = Math.min(radius, minArmLen * 0.35);
            // Sàn tuyệt đối: radius phải ≥ halfWidth nhánh rộng nhất (để tròn tâm phủ kín)
            radius = Math.max(maxHalfWidth, radius);
        }

        return new IntersectionRenderData(centerX, centerY, radius, getIntersectionType(), arms);
    }

    @Override
    public String toString() {
        return String.format("%s tại (%.1f, %.1f) — %d/%d đường",
                getIntersectionType(), centerX, centerY,
                connections.size(), getExpectedRoadCount());
    }
}