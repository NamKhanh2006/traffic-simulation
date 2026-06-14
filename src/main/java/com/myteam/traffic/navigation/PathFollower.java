package com.myteam.traffic.navigation;

import com.myteam.traffic.model.geometry.Direction;
import com.myteam.traffic.model.geometry.Position;
import com.myteam.traffic.vehicle.Vehicle;

/**
 * Tiến phương tiện dọc quỹ đạo giao lộ (tăng {@code pathProgress}).
 */
public class PathFollower {

    public void advance(Vehicle vehicle, double deltaTime) {
        if (vehicle.getSpeed() <= 0) return;
        IntersectionPath path = vehicle.getActivePath();
        if (path == null) return;
        double nextS = vehicle.getPathProgress() + vehicle.getSpeed() * deltaTime;
        vehicle.setPathProgress(nextS);
        syncPose(vehicle, path, nextS);
    }

    public void syncPose(Vehicle vehicle, IntersectionPath path, double s) {
        double clamped = Math.min(s, path.getPathLength());
        double[] sample = path.sampleAt(clamped);
        vehicle.setPosition(new Position(sample[0], sample[1]));
        vehicle.setDirection(new Direction(sample[2]));
    }
}
