package com.myteam.traffic.navigation;

import com.myteam.traffic.model.infrastructure.ConnectionPoint;
import com.myteam.traffic.model.infrastructure.intersection.Intersection;

public final class IntersectionPath {

    private final Intersection intersection;
    private final ConnectionPoint entry;
    private final ConnectionPoint exit;

    private final double startX, startY;
    private final double endX, endY;
    private final double cp1X, cp1Y;
    private final double cp2X, cp2Y;
    private final double pathLength;

    private final double centerX;
    private final double centerY;
    private final double radius;
    private final double startAngleRad;
    private final double sweepRad;

    public IntersectionPath(Intersection intersection, ConnectionPoint entry, ConnectionPoint exit,
                            double startX, double startY, double startHeadingDeg,
                            double endX, double endY, double endHeadingDeg) {
        this.intersection = intersection;
        this.entry = entry;
        this.exit = exit;
        this.startX = startX;
        this.startY = startY;
        this.endX = endX;
        this.endY = endY;

        this.radius = intersection.getRenderData() != null ? intersection.getRenderData().radius : 40.0;

        // FIX LỖI ĐƯỜNG CONG QUAY MÒNG MÒNG:
        // Cân bằng hệ số pull. Ép pull không bao giờ được lớn hơn 80% bán kính giao lộ.
        // Điều này đảm bảo đường cong luôn mượt và nằm bên trong ngã tư, không bị vọt ra ngoài tạo nét vòng.
        double dist = Math.hypot(endX - startX, endY - startY);
        double pull = Math.min(dist * 0.45, this.radius * 0.8);

        double startRad = Math.toRadians(startHeadingDeg);
        this.cp1X = startX + Math.cos(startRad) * pull;
        this.cp1Y = startY + Math.sin(startRad) * pull;

        double endRad = Math.toRadians(endHeadingDeg);
        this.cp2X = endX - Math.cos(endRad) * pull;
        this.cp2Y = endY - Math.sin(endRad) * pull;

        // Tính chiều dài quỹ đạo
        this.pathLength = Math.max(1.0, dist * 1.15);

        this.centerX = intersection.getCenterX();
        this.centerY = intersection.getCenterY();
        this.startAngleRad = startRad;

        double sweep = endRad - startRad;
        while (sweep <= -Math.PI) sweep += 2 * Math.PI;
        while (sweep > Math.PI) sweep -= 2 * Math.PI;
        this.sweepRad = sweep;
    }

    public Intersection getIntersection() { return intersection; }
    public ConnectionPoint getEntry()     { return entry; }
    public ConnectionPoint getExit()      { return exit; }
    public double getPathLength()         { return pathLength; }
    public double getCenterX()            { return centerX; }
    public double getCenterY()            { return centerY; }
    public double getRadius()             { return radius; }
    public double getStartAngleRad()      { return startAngleRad; }
    public double getSweepRad()           { return sweepRad; }

    public double[] sampleAt(double s) {
        double t = Math.max(0.0, Math.min(1.0, s / pathLength));
        double u = 1.0 - t;

        double currentX = (u*u*u * startX) + (3*u*u*t * cp1X) + (3*u*t*t * cp2X) + (t*t*t * endX);
        double currentY = (u*u*u * startY) + (3*u*u*t * cp1Y) + (3*u*t*t * cp2Y) + (t*t*t * endY);

        double dx = 3*u*u * (cp1X - startX) + 6*u*t * (cp2X - cp1X) + 3*t*t * (endX - cp2X);
        double dy = 3*u*u * (cp1Y - startY) + 6*u*t * (cp2Y - cp1Y) + 3*t*t * (endY - cp2Y);

        double angleDeg = Math.toDegrees(Math.atan2(dy, dx));

        return new double[] { currentX, currentY, angleDeg };
    }
}