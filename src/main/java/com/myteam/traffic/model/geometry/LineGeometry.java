package com.myteam.traffic.model.geometry;

public class LineGeometry {
	private Position start;
	private Position end;
	private float width;
	
	// Getters and setters
	public Position getStart() {
		return start;
	}
	public void setStart(Position start) {
		this.start = start;
	}
	public Position getEnd() {
		return end;
	}
	public void setEnd(Position end) {
		this.end = end;
	}
	public float getWidth() {
		return width;
	}
	public void setWidth(float width) {
		this.width = width;
	}
	
	// Constructor
	public LineGeometry(Position start, Position end, float width) {
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
