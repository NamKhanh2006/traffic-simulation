package com.myteam.traffic.vehicle;

import com.myteam.traffic.behavior.DriverBehavior;
import com.myteam.traffic.model.geometry.*;

public abstract class Vehicle {
    //protected double x;
    //protected double y;
	protected Position position;
    //protected double angle; // Góc rẽ
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
        x += speed * Math.cos(angle);
        y += speed * Math.sin(angle);
    }

    public void turnLeft() {
        angle -= Math.PI / 2; // Rẽ trái 90 độ
    }

    public void turnRight() {
        angle += Math.PI / 2; // Rẽ phải 90 độ
    }

    public void accelerate() {
        speed = Math.min(maxSpeed, speed + 1);
    }

    public void slowDown() {
        speed = Math.max(0, speed - 1);
    }

    public void stop() {
        speed = 0;
    }

    /*
    public double getX() {
        return x;
    }
    
    
    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public double getAngle() {
        return angle;
    }

    public void setAngle(double angle) {
        this.angle = angle;
    }
    */

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
