package com.myteam.traffic.model.geometry;

public class LineGeometry implements Geometry {
	private Point start;
	private Point end;
	private float width;
	
	// Getters and setters
	public Point getStart() {
		return start;
	}
	public void setStart(Point start) {
		this.start = start;
	}
	public Point getEnd() {
		return end;
	}
	public void setEnd(Point end) {
		this.end = end;
	}
	public float getWidth() {
		return width;
	}
	public void setWidth(float width) {
		this.width = width;
	}
	
	// Constructor
	public LineGeometry(Point start, Point end, float width) {
		super();
		this.start = start;
		this.end = end;
		this.width = width;
	}
	
	
	
	
	/*
    public boolean intersects(Vehicle v) {
        
        return true; // TODO: refine sau
    }
    */
	
	
	
}
