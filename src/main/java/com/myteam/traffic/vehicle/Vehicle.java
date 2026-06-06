package com.myteam.traffic.vehicle;

import com.myteam.traffic.behavior.DriverBehavior;
import com.myteam.traffic.model.geometry.Direction;
import com.myteam.traffic.model.geometry.Position;
import com.myteam.traffic.model.infrastructure.Lane;
import com.myteam.traffic.model.infrastructure.RoadSegment;
import com.myteam.traffic.model.infrastructure.intersection.Intersection;
import com.myteam.traffic.navigation.IntersectionPath;

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

    private TravelMode travelMode = TravelMode.ON_SEGMENT;

    /** Đoạn đường hiện tại — null khi {@link TravelMode#ON_INTERSECTION_PATH}. */
    protected RoadSegment currentSegment;

    /** Làn hiện tại — null khi {@link TravelMode#ON_INTERSECTION_PATH}. */
    protected Lane currentLane;

    /** Tiến độ trên segment [0, 1]. */
    private double segmentProgress;

    /** Giao lộ hiện tại — null khi {@link TravelMode#ON_SEGMENT}. */
    protected Intersection currentIntersection;

    /** Quỹ đạo cung đang đi — null khi {@link TravelMode#ON_SEGMENT}. */
    private IntersectionPath activePath;

    /** Tiến độ dọc quỹ đạo [0, pathLength]. */
    private double pathProgress;

    /** Nhánh ra dự kiến tại giao lộ sắp tới. */
    private PlannedExit plannedExit = PlannedExit.NONE;

    public Vehicle(Position position, Direction direction, DriverBehavior behavior) {
        this.position = position;
        this.direction = direction;
        this.behavior = behavior;
        this.speed = 0;
    }

    // ── Đặt xe lên mạng lưới (gọi từ TrafficController khi spawn) ──

    public void placeOnSegment(RoadSegment segment, Lane lane, double t) {
        this.travelMode = TravelMode.ON_SEGMENT;
        this.currentSegment = segment;
        this.currentLane = lane;
        this.segmentProgress = clamp01(t);
        this.currentIntersection = null;
        this.activePath = null;
        this.pathProgress = 0;
        syncPoseFromSegment();
    }

    public void enterIntersectionPath(IntersectionPath path, Intersection intersection) {
        this.travelMode = TravelMode.ON_INTERSECTION_PATH;
        this.activePath = path;
        this.currentIntersection = intersection;
        this.pathProgress = 0;
        this.currentSegment = null;
        this.currentLane = null;
        this.segmentProgress = 0;
        double[] sample = path.sampleAt(0);
        this.position = new Position(sample[0], sample[1]);
        this.direction = new Direction(sample[2]);
    }

    public void exitToSegment(RoadSegment segment, Lane lane, double t) {
        this.travelMode = TravelMode.ON_SEGMENT;
        this.currentSegment = segment;
        this.currentLane = lane;
        this.segmentProgress = clamp01(t);
        this.currentIntersection = null;
        this.activePath = null;
        this.pathProgress = 0;
        syncPoseFromSegment();
    }

    // ── Di chuyển ─────────────────────────────────────────────

    /** Tiến trên segment theo {@code speed} và độ dài đoạn đường. */
    public void advanceOnSegment() {
        if (currentSegment == null || currentLane == null) {
            moveForwardEuclidean();
            return;
        }
        if (speed <= 0) {
            return;
        }
        double length = currentSegment.getLength();
        if (length <= 0) {
            return;
        }
        segmentProgress = Math.min(1.0, segmentProgress + speed / length);
        syncPoseFromSegment();
    }

    /** Di chuyển tự do khi chưa gắn segment (tương thích ngược). */
    public void moveForward() {
        if (travelMode == TravelMode.ON_SEGMENT && currentSegment != null) {
            advanceOnSegment();
        } else if (travelMode == TravelMode.ON_INTERSECTION_PATH) {
            // PathFollower gọi từ controller; fallback không làm gì nếu speed = 0
        } else {
            moveForwardEuclidean();
        }
    }

    private void moveForwardEuclidean() {
        if (speed <= 0) {
            return;
        }
        double newX = position.getX() + speed * Math.cos(direction.toRadians());
        double newY = position.getY() + speed * Math.sin(direction.toRadians());
        this.position = new Position(newX, newY);
    }

    private void syncPoseFromSegment() {
        if (currentSegment == null || currentLane == null) {
            return;
        }
        double[] pose = currentSegment.getPositionOnLane(currentLane.getIndex(), segmentProgress);
        this.position = new Position(pose[0], pose[1]);
        this.direction = new Direction(pose[2]);
    }

    public boolean isIntersectionPathComplete() {
        return activePath != null && pathProgress >= activePath.getPathLength();
    }

    // ── Tốc độ ────────────────────────────────────────────────

    public void accelerate() {
        speed = Math.min(maxSpeed, speed + 1);
    }

    public void slowDown() {
        speed = Math.max(0, speed - 1);
    }

    public void stop() {
        speed = 0;
    }

    /** Stub — đổi làn trên segment sẽ bổ sung sau. */
    public void changeLane() {
        System.out.printf("[%s] changeLane (stub)%n", type);
    }

    public void uTurn() {
        System.out.printf("[%s] uTurn (stub)%n", type);
    }

    // ── plannedExit ───────────────────────────────────────────

    public PlannedExit getPlannedExit() {
        return plannedExit;
    }

    public void setPlannedExit(PlannedExit plannedExit) {
        this.plannedExit = plannedExit != null ? plannedExit : PlannedExit.NONE;
    }

    public void clearPlannedExit() {
        this.plannedExit = PlannedExit.NONE;
    }

    // ── Getters / setters ─────────────────────────────────────

    public TravelMode getTravelMode() {
        return travelMode;
    }

    public RoadSegment getCurrentSegment() {
        return travelMode == TravelMode.ON_SEGMENT ? currentSegment : null;
    }

    public Lane getCurrentLane() {
        return travelMode == TravelMode.ON_SEGMENT ? currentLane : null;
    }

    public Intersection getCurrentIntersection() {
        return travelMode == TravelMode.ON_INTERSECTION_PATH ? currentIntersection : null;
    }

    public double getSegmentProgress() {
        return segmentProgress;
    }

    public IntersectionPath getActivePath() {
        return activePath;
    }

    public double getPathProgress() {
        return pathProgress;
    }

    public void setPathProgress(double pathProgress) {
        this.pathProgress = Math.max(0, pathProgress);
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

    public double getY() {
        return position.getY();
    }

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

    /** Stub: xe khẩn cấp coi như còi đang bật khi {@code isEmergency}. */
    public boolean isSirenOn() {
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

    public abstract void honk();

    private static double clamp01(double t) {
        return Math.max(0.0, Math.min(1.0, t));
    }
}
