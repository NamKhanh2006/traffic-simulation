package com.myteam.traffic.behavior;

import com.myteam.traffic.behavior.common.Action;
import com.myteam.traffic.behavior.common.DistanceKeeping;
import com.myteam.traffic.context.RoadContext;
import com.myteam.traffic.navigation.IntersectionPath;
import com.myteam.traffic.vehicle.TravelMode;
import com.myteam.traffic.vehicle.Vehicle;
import com.myteam.traffic.model.geometry.Position;

public class NormalDriver implements DriverBehavior {

    private static final double SAFE_GAP_INTERSECTION = 60.0; // Ngưỡng an toàn trong giao lộ

    @Override
    public Action decideAction(Vehicle v, RoadContext context) {
        if (v.getTravelMode() == TravelMode.ON_INTERSECTION_PATH) {
            return handleIntersectionPath(v, context);
        } else {
            return handleSegment(v, context);
        }
    }

    /*
    private Action handleIntersectionPath(Vehicle v, RoadContext context) {
        IntersectionPath myPath = v.getActivePath();
        if (myPath == null) return Action.ACCELERATE;

        double dt = context.getDeltaTime();

        // Lấy giá trị snapshot (đầu tick) của chính xe
        double myProgress = context.getSnapshotPathProgress(v).orElse(v.getPathProgress());
        double mySpeed = context.getSnapshotSpeed(v).orElse(v.getSpeed());
        double myNextProgress = myProgress + mySpeed * dt;

        // Nếu sắp ra khỏi giao lộ, không kiểm tra nữa
        if (myNextProgress >= myPath.getPathLength()) {
            return Action.ACCELERATE;
        }

        // Vị trí tương lai của mình
        double[] myFuture = myPath.sampleAt(myNextProgress);
        Position myFuturePos = new Position(myFuture[0], myFuture[1]);

        boolean mustStop = false;

        for (Vehicle other : context.getNearbyVehicles()) {
            if (other == v) continue;
            if (other.getTravelMode() != TravelMode.ON_INTERSECTION_PATH) continue;

            IntersectionPath otherPath = other.getActivePath();
            if (otherPath == null) continue;

            // Lấy snapshot của xe khác
            double otherProgress = context.getSnapshotPathProgress(other).orElse(other.getPathProgress());
            double otherSpeed = context.getSnapshotSpeed(other).orElse(other.getSpeed());
            double otherNextProgress = otherProgress + otherSpeed * dt;

            // Bỏ qua nếu xe khác đã ra khỏi giao lộ
            if (otherNextProgress >= otherPath.getPathLength()) continue;

            // Chỉ quan tâm xe phía trước (có pathProgress lớn hơn)
            if (otherProgress <= myProgress) continue;

            double[] otherFuture = otherPath.sampleAt(otherNextProgress);
            Position otherFuturePos = new Position(otherFuture[0], otherFuture[1]);

            double futureGap = myFuturePos.distanceTo(otherFuturePos);
            if (futureGap < 20.0){
                return Action.STOP;
            }
            else if (futureGap < SAFE_GAP_INTERSECTION) {
                //mustStop = true;
                //break;
                return Action.SLOW_DOWN;
            }
        }

        return mustStop ? Action.STOP : Action.ACCELERATE;
    }
    */
    private Action handleIntersectionPath(Vehicle v, RoadContext context) {
    IntersectionPath myPath = v.getActivePath();
    if (myPath == null) return Action.ACCELERATE;

    Position myPos = context.getSnapshotPosition(v).orElse(v.getPosition());
    
    for (Vehicle other : context.getNearbyVehicles()) {
        if (other == v) continue;
        if (other.getTravelMode() != TravelMode.ON_INTERSECTION_PATH) continue;
        
        Position otherPos = context.getSnapshotPosition(other).orElse(other.getPosition());
        double dist = myPos.distanceTo(otherPos);
        if (dist < 30.0) {  // ngưỡng an toàn
            return Action.STOP;
        }
    }
    
        if (v.getSpeed() < v.getMaxSpeed()) return Action.ACCELERATE;
        return Action.MOVE_FORWARD;
    }

    private Action handleSegment(Vehicle v, RoadContext context) {
        Vehicle front = context.getNearestFrontVehicle();

        if (front != null) {
            if (DistanceKeeping.isImminentCollision(v, front)) {
                return Action.STOP;
            }
            if (DistanceKeeping.timeToCollision(v, front) < DistanceKeeping.SAFE_TTC) {
                return Action.SLOW_DOWN;
            }
            if (front.getSpeed() < v.getSpeed() &&
                DistanceKeeping.timeToCollision(v, front) > DistanceKeeping.SAFE_TTC + 1.0) {
                return Action.OVERTAKE;
            }
        }

        if (context.hasRedLightAhead()) {
            return Action.STOP;
        }

        if (v.getSpeed() < v.getMaxSpeed()) {
            return Action.ACCELERATE;
        }

        return Action.MOVE_FORWARD;
    }

    @Override
    public Action handleRejection(Vehicle v, RoadContext context, Action rejected) {
        return switch (rejected) {
            case OVERTAKE, CHANGE_LANE -> Action.SLOW_DOWN;
            case ACCELERATE            -> Action.MOVE_FORWARD;
            default                    -> Action.SLOW_DOWN;
        };
    }
}