package com.myteam.traffic.vehicle;

import com.myteam.traffic.behavior.DriverBehavior;
import com.myteam.traffic.model.geometry.*;

public abstract class Vehicle {
	protected Position position;
	protected Direction direction;
    protected double width;
    protected double height;
    protected double speed;
    protected double maxSpeed;
    protected VehicleType type;
    protected boolean isEmergency;
    protected DriverBehavior behavior;

    // Constructor chuẩn
    public Vehicle(Position position, Direction direction, DriverBehavior behavior) {
        this.position = position;
        this.direction = direction;
        this.behavior = behavior;
        this.speed = 0; // Mặc định đứng yên
    }

    // Di chuyển 2D
    public void moveForward() {
        double newX = position.getX() + speed * Math.cos(direction.toRadians());
        double newY = position.getY() + speed * Math.sin(direction.toRadians());
        this.position = new Position(newX, newY);
    }

    /*
    public void turnLeft() {
        angle -= Math.PI / 2; // Rẽ trái 90 độ
    }

    public void turnRight() {
        angle += Math.PI / 2; // Rẽ phải 90 độ
    }
    */

    public void accelerate() {
        speed = Math.min(maxSpeed, speed + 1);
    }

    public void slowDown() {
        speed = Math.max(0, speed - 1);
    }

    public void stop() {
        speed = 0;
    }
    
    public Position getPosition() {
    	return position;
    }
    
    public void setPosition(Position position) {
    	this.position = position;
    }

    public double getX() {
        return position.getX();
    }
    
    /*
    public void setX(double x) {
        this.x = x;
    }
	*/
    
    public double getY() {
        return position.getY();
    }

    /*
    public void setY(double y) {
        this.y = y;
    }
    */

    public Direction getDirection() {
        return direction;
    }

    public void setDirection(Direction direction) {
        this.direction = direction;
    }

    public double getWidth() {
        return width;
    }

    public void setWidth(double width) {
        this.width = width;
    }

    public double getHeight() {
        return height;
    }

    public void setHeight(double height) {
        this.height = height;
    }

    public double getSpeed() {
        return speed;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    public double getMaxSpeed() {
        return maxSpeed;
    }

    public void setMaxSpeed(double maxSpeed) {
        this.maxSpeed = maxSpeed;
    }

    public VehicleType getType() {
        return type;
    }

    public void setType(VehicleType type) {
        this.type = type;
    }

    public boolean isEmergency() {
        return isEmergency;
    }

    public void setEmergency(boolean emergency) {
        isEmergency = emergency;
    }

    public DriverBehavior getBehavior() {
        return behavior;
    }

    public void setBehavior(DriverBehavior behavior) {
        this.behavior = behavior;
    }

    public abstract void honk(); // Hàm trừu tượng để các xe con tự định nghĩa
}
