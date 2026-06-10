// NormalDriver.java
package com.myteam.traffic.behavior;

import com.myteam.traffic.behavior.common.Action;
import com.myteam.traffic.behavior.bt.*;
import com.myteam.traffic.context.RoadContext;
import com.myteam.traffic.vehicle.Vehicle;
import com.myteam.traffic.behavior.common.DistanceKeeping;

public class NormalDriver implements DriverBehavior {
    private final BTNode decisionTree;

    public NormalDriver() {
        this.decisionTree = new SelectorNode(
            new LeafNodes.HasRedLightAhead(),
            new LeafNodes.IsTooCloseToFront(),
            new LeafNodes.HasEmergencyNearby(),
            (v, ctx) -> Action.MOVE_FORWARD
        );
    }

    @Override
    public Action decideAction(Vehicle v, RoadContext context) {
        Vehicle front = context.getNearestFrontVehicle();
        // 1. Dừng khẩn cấp nếu va chạm sắp xảy ra
        if (DistanceKeeping.isImminentCollision(v, front)) {
            return Action.STOP;
        }
        // 2. Giảm tốc nếu TTC không an toàn
        if (DistanceKeeping.timeToCollision(v, front) < DistanceKeeping.SAFE_TTC) {
            return Action.SLOW_DOWN;
        }
        // 3. Tuân thủ đèn đỏ
        if (context.hasRedLightAhead()) {
            return Action.STOP;
        }
        // 4. Tăng tốc nếu an toàn
        if (v.getSpeed() < v.getMaxSpeed() && !context.isTooCloseToFront()) {
            return Action.ACCELERATE;
        }
        return Action.MOVE_FORWARD;
    }

    @Override
    public Action handleRejection(Vehicle v, RoadContext context, Action rejected) {
        switch (rejected) {
            case OVERTAKE: case CHANGE_LANE: return Action.SLOW_DOWN;
            default: return Action.STOP;
        }
    }
}
