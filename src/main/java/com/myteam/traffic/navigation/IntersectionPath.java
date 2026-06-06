package com.myteam.traffic.navigation;

import com.myteam.traffic.model.infrastructure.ConnectionPoint;
import com.myteam.traffic.model.infrastructure.intersection.Intersection;

/**
 * Quỹ đạo cung tròn qua giao lộ — immutable sau khi tạo.
 */
public final class IntersectionPath {

    private final Intersection intersection;
    private final ConnectionPoint entry;
    private final ConnectionPoint exit;
    private final double centerX;
    private final double centerY;
    private final double radius;
    private final double startAngleRad;
    private final double sweepRad;
    private final double pathLength;

    public IntersectionPath(Intersection intersection,
                            ConnectionPoint entry,
                            ConnectionPoint exit,
                            double centerX,
                            double centerY,
                            double radius,
                            double startAngleRad,
                            double sweepRad) {
        this.intersection = intersection;
        this.entry = entry;
        this.exit = exit;
        this.centerX = centerX;
        this.centerY = centerY;
        this.radius = radius;
        this.startAngleRad = startAngleRad;
        this.sweepRad = sweepRad;
        this.pathLength = Math.max(1.0, Math.abs(radius * sweepRad));
    }

    public Intersection getIntersection()       { return intersection; }
    public ConnectionPoint getEntry()           { return entry; }
    public ConnectionPoint getExit()            { return exit; }
    public double getCenterX()                  { return centerX; }
    public double getCenterY()                  { return centerY; }
    public double getRadius()                   { return radius; }
    public double getStartAngleRad()            { return startAngleRad; }
    public double getSweepRad()                 { return sweepRad; }
    public double getPathLength()               { return pathLength; }

    /** Góc quanh tâm (radian) tại tiến độ {@code s} dọc quỹ đạo. */
    public double arcAngleAt(double s) {
        double t = Math.max(0.0, Math.min(1.0, s / pathLength));
        return startAngleRad + t * sweepRad;
    }

    /** Vị trí và hướng tiếp tuyến tại tiến độ {@code s} dọc quỹ đạo [0, pathLength]. */
    public double[] sampleAt(double s) {
        double t = Math.max(0.0, Math.min(1.0, s / pathLength));
        double angle = startAngleRad + t * sweepRad;
        double x = centerX + radius * Math.cos(angle);
        double y = centerY + radius * Math.sin(angle);
        double tangentDeg = Math.toDegrees(angle + Math.copySign(Math.PI / 2.0, sweepRad));
        return new double[] { x, y, tangentDeg };
    }
}
