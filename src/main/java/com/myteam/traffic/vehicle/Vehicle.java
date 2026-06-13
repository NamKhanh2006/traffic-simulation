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
    protected double acceleration;
    protected double maxSpeed;
    protected VehicleType type;
    protected boolean isEmergency;
    protected DriverBehavior behavior;

    private TravelMode travelMode = TravelMode.ON_SEGMENT;
    protected RoadSegment currentSegment;
    protected Lane currentLane;
    private double segmentProgress;
    protected Intersection currentIntersection;
    private IntersectionPath activePath;
    private double pathProgress;
    private PlannedExit plannedExit = PlannedExit.NONE;

    // Biến hỗ trợ hiệu ứng chuyển làn mượt mà
    protected double laneChangeProgress = 1.0;
    protected Lane previousLane = null;

    public Vehicle(Position position, Direction direction, DriverBehavior behavior) {
        this.position = position;
        this.direction = direction;
        this.behavior = behavior;
        this.speed = 0;
    }

    public void placeOnSegment(RoadSegment segment, Lane lane, double t) {
        this.travelMode = TravelMode.ON_SEGMENT;
        this.currentSegment = segment;
        this.currentLane = lane;
        this.segmentProgress = clamp01(t);
        this.currentIntersection = null;
        this.activePath = null;
        this.pathProgress = 0;
        this.laneChangeProgress = 1.0; // Reset
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

        // Lấy chính xác vị trí và góc từ đường Bezier
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
        this.laneChangeProgress = 1.0; // Reset
        syncPoseFromSegment();
    }

    public void syncPositionFromSegment() {
        if (currentSegment == null || currentLane == null) return;

        double[] targetPose = currentSegment.getPositionOnLane(currentLane.getIndex(), segmentProgress);

        // NẾU ĐANG CHUYỂN LÀN -> Tính toán nội suy (Interpolation) để lướt mượt
        if (laneChangeProgress < 1.0 && previousLane != null) {
            double[] sourcePose = currentSegment.getPositionOnLane(previousLane.getIndex(), segmentProgress);

            // Lướt X, Y từ từ sang làn mới
            double x = sourcePose[0] + (targetPose[0] - sourcePose[0]) * laneChangeProgress;
            double y = sourcePose[1] + (targetPose[1] - sourcePose[1]) * laneChangeProgress;

            // Tạo góc nghiêng đầu xe (Khoảng 15 độ)
            double tiltOffset = 15.0;
            if (currentLane.getIndex() < previousLane.getIndex()) {
                tiltOffset = -15.0; // Rẽ trái
            }
            if (currentLane.getDirection() == Lane.Direction.BACKWARD) {
                tiltOffset = -tiltOffset; // Đảo ngược nếu đang đi lùi
            }

            // Hiệu ứng hình sin: Nghiêng mạnh ở giữa quá trình, thẳng lái lại khi hoàn thành
            double tilt = tiltOffset * Math.sin(laneChangeProgress * Math.PI);

            this.position = new Position(x, y);
            this.direction = new Direction(targetPose[2] + tilt);
        } else {
            // Đi thẳng bình thường
            this.position = new Position(targetPose[0], targetPose[1]);
            this.direction = new Direction(targetPose[2]);
        }
    }

    public void syncPositionFromPath() {
        if (activePath == null) return;

        // Bỏ hết logic làm mượt đi, tin tưởng 100% vào Bezier Curve
        double[] sample = activePath.sampleAt(pathProgress);
        this.position = new Position(sample[0], sample[1]);
        this.direction = new Direction(sample[2]);
    }

    private void syncPoseFromSegment() {
        syncPositionFromSegment();
    }

    public boolean isIntersectionPathComplete() {
        return activePath != null && pathProgress >= activePath.getPathLength();
    }

    public void applyAcceleration(double acc, double deltaTime) {
        this.acceleration = acc;
        this.speed = Math.max(0, Math.min(maxSpeed, speed + acceleration * deltaTime));
    }

    // --- LOGIC CHUYỂN LÀN ---
    public void changeLaneIndex(int newLaneIndex) {
        // Chỉ bắt đầu chuyển nếu đã hoàn thành lần chuyển trước đó
        if (this.currentLane != null && this.currentLane.getIndex() == newLaneIndex) return;

        this.previousLane = this.currentLane;
        this.currentLane = currentSegment.getLanes().get(newLaneIndex);
        this.laneChangeProgress = 0.0; // Bắt đầu animation
    }

    public double getLaneChangeProgress() { return laneChangeProgress; }
    public void setLaneChangeProgress(double progress) { this.laneChangeProgress = progress; }

    public PlannedExit getPlannedExit() { return plannedExit; }
    public void setPlannedExit(PlannedExit plannedExit) { this.plannedExit = plannedExit != null ? plannedExit : PlannedExit.NONE; }
    public void clearPlannedExit() { this.plannedExit = PlannedExit.NONE; }

    public TravelMode getTravelMode() { return travelMode; }
    public RoadSegment getCurrentSegment() { return travelMode == TravelMode.ON_SEGMENT ? currentSegment : null; }
    public Lane getCurrentLane() { return travelMode == TravelMode.ON_SEGMENT ? currentLane : null; }
    public Intersection getCurrentIntersection() { return travelMode == TravelMode.ON_INTERSECTION_PATH ? currentIntersection : null; }
    public double getSegmentProgress() { return segmentProgress; }
    public IntersectionPath getActivePath() { return activePath; }
    public double getPathProgress() { return pathProgress; }
    public void setPathProgress(double pathProgress) { this.pathProgress = Math.max(0, pathProgress); }
    public Position getPosition() { return position; }
    public void setPosition(Position position) { this.position = position; }
    public double getX() { return position.getX(); }
    public double getY() { return position.getY(); }
    public Direction getDirection() { return direction; }
    public void setDirection(Direction direction) { this.direction = direction; }
    public double getWidth() { return width; }
    public void setWidth(double width) { this.width = width; }
    public double getHeight() { return height; }
    public void setHeight(double height) { this.height = height; }
    public double getSpeed() { return speed; }
    public void setSpeed(double speed) { this.speed = Math.max(0, Math.min(maxSpeed, speed)); }
    public double getMaxSpeed() { return maxSpeed; }
    public void setMaxSpeed(double maxSpeed) { this.maxSpeed = maxSpeed; }
    public VehicleType getType() { return type; }
    public void setType(VehicleType type) { this.type = type; }
    public boolean isEmergency() { return isEmergency; }
    public boolean isSirenOn() { return isEmergency; }
    public void setEmergency(boolean emergency) { isEmergency = emergency; }
    public DriverBehavior getBehavior() { return behavior; }
    public void setBehavior(DriverBehavior behavior) { this.behavior = behavior; }
    public abstract void honk();

    private static double clamp01(double t) { return Math.max(0.0, Math.min(1.0, t)); }
    public void setSegmentProgress(double progress) { this.segmentProgress = clamp01(progress); }
    public void setCurrentLane(Lane lane) { this.currentLane = lane; }
    public void setCurrentSegment(RoadSegment segment) { this.currentSegment = segment; }
    public void setActivePath(IntersectionPath path) { this.activePath = path; }
    public void setCurrentIntersection(Intersection inter) { this.currentIntersection = inter; }

    public double getMaxAcceleration() { return 2.0; }
    public double getComfortableDeceleration() { return 3.0; }
    public void slowDown(double delta) { setSpeed(Math.max(0, getSpeed() - delta)); }
}