package com.myteam.traffic.geometry;

public class LineGeometry implements Geometry {
    private Point start;
    private Point end;
    private float width;

    public LineGeometry(Point start, Point end, float width) {
        this.start = start;
        this.end = end;
        this.width = width;
    }

    public Point getStart() {
        return start;
    }
    public Point getEnd() {
        return end;
    }
    public float getWidth() {
        return width;
    }
}