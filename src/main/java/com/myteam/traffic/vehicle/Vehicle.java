package com.myteam.traffic.vehicle;

import com.myteam.traffic.behavior.DriverBehavior;
import com.myteam.traffic.model.geometry.Direction;
import com.myteam.traffic.model.geometry.Position;
import com.myteam.traffic.model.infrastructure.Lane;
import com.myteam.traffic.model.infrastructure.RoadSegment;
import com.myteam.traffic.model.infrastructure.intersection.Intersection;
import com.myteam.traffic.navigation.IntersectionPath;
import java.util.List;

public abstract class Vehicle {
    protected Position position;
    protected Direction direction;
    protected double width;
    protected double height;
    protected double speed;
    protected double acceleration;
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

    // ─── Đồng bộ vị trí từ dữ liệu hiện tại ────────────────────────────
    public void syncPositionFromSegment() {
        if (currentSegment == null || currentLane == null) return;
        
        double[] pose = currentSegment.getPositionOnLane(
            currentLane.getIndex(), 
            segmentProgress
        );
        this.position = new Position(pose[0], pose[1]);
        this.direction = new Direction(pose[2]);
    }

    public void syncPositionFromPath() {
        if (activePath == null) return;
        
        double[] sample = activePath.sampleAt(pathProgress);
        this.position = new Position(sample[0], sample[1]);
        this.direction = new Direction(sample[2]);
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

    // --- API Vật lý nguyên tử (Atomic Physical API) ---

    public void applyAcceleration(double acc, double deltaTime) {
        this.acceleration = acc;
        this.speed = Math.max(0, Math.min(maxSpeed, speed + acceleration * deltaTime));
    }

    public void changeLaneIndex(int newLaneIndex) {
        // Chỉ thực hiện việc gán lane, logic an toàn nằm ở Strategy
        this.currentLane = currentSegment.getLanes().get(newLaneIndex);
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
        //this.speed = speed;
        this.speed = Math.max(0, Math.min(maxSpeed, speed));
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

    // ─── Cập nhật trạng thái nguyên tử ─────────────────────────────────
    public void setSegmentProgress(double progress) {
        this.segmentProgress = clamp01(progress);
    }

    public void setCurrentLane(Lane lane) {
        this.currentLane = lane;
    }

    public void setCurrentSegment(RoadSegment segment) {
        this.currentSegment = segment;
    }

    public void setActivePath(IntersectionPath path) {
        this.activePath = path;
    }

    public void setCurrentIntersection(Intersection inter) {
        this.currentIntersection = inter;
    }

}
