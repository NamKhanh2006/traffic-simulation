// FuzzyAggressiveDriver.java
package com.myteam.traffic.behavior;

import com.myteam.traffic.behavior.common.Action;
import com.myteam.traffic.context.RoadContext;
import com.myteam.traffic.vehicle.Vehicle;
import java.util.Random;

public class AggressiveDriver implements DriverBehavior {
    private final Random random = new Random();

    // Membership functions
    private double veryClose(double gap) { 
        return Math.max(0, Math.min(1, (3.0 - gap) / 3.0)); 
    }
    private double close(double gap) { 
        return Math.max(0, Math.min(1, (gap - 1.0) / 4.0, (8.0 - gap) / 5.0)); 
    }
    private double far(double gap) { 
        return Math.max(0, Math.min(1, (gap - 5.0) / 10.0)); 
    }

    private double muchSlower(double relSpeed) { 
        return Math.max(0, Math.min(1, -relSpeed / 10.0)); 
    }
    private double equal(double relSpeed) { 
        return Math.max(0, 1 - Math.abs(relSpeed) / 3.0); 
    }
    private double muchFaster(double relSpeed) { 
        return Math.max(0, Math.min(1, relSpeed / 10.0)); 
    }

    @Override
    public Action decideAction(Vehicle v, RoadContext context) {
        Vehicle front = context.getNearestFrontVehicle();
        // Ưu tiên đạt max speed
        if (v.getSpeed() < v.getMaxSpeed() && 
            !DistanceKeeping.isCollisionImminent(v, front)) {
            return Action.ACCELERATE;
        }
        // Vượt nếu xe trước chậm và đủ an toàn
        if (front != null && front.getSpeed() < v.getSpeed() - 5) {
            return Action.OVERTAKE;
        }

        // Giảm tốc nếu TTC quá nhỏ
        if (DistanceKeeping.timeToCollision(v, front) < DistanceKeeping.SAFE_TTC) {
            return Action.SLOW_DOWN;
        }
        if (front == null) return Action.MOVE_FORWARD;

        double gap = front.getX() - v.getX();
        double relSpeed = v.getSpeed() - front.getSpeed();

        // Fuzzy rules
        double overtake = Math.min(close(gap), muchSlower(relSpeed));
        double honk = Math.min(veryClose(gap), equal(relSpeed));
        double slow = Math.min(veryClose(gap), muchFaster(relSpeed));

        // Defuzzification – chọn hành động có độ kích hoạt cao nhất
        if (overtake >= honk && overtake >= slow && overtake > 0.3) return Action.OVERTAKE;
        if (honk >= slow && honk > 0.4) return Action.HONK;
        if (slow > 0.2) return Action.SLOW_DOWN;

        return Action.MOVE_FORWARD;
    }

    @Override
    public Action handleRejection(Vehicle v, RoadContext context, Action rejected) {
        switch (rejected) {
            case OVERTAKE: return Action.HONK;
            case ACCELERATE: return Action.MOVE_FORWARD;
            default: return Action.SLOW_DOWN;
        }
    }
}
