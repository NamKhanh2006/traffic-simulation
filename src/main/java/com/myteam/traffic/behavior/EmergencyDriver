package com.myteam.traffic.behavior;

import traffic.common.Action;
import traffic.model.context.RoadContext;
import traffic.model.vehicle.Vehicle;

public class EmergencyDriver implements DriverBehavior {

    @Override
    public Action decideAction(Vehicle v, RoadContext c) {

        // Luôn bật còi
        if (!v.isSirenOn()) {
            return Action.HONK;
        }

        // Có xe trước → vượt
        if (c.getFrontVehicle() != null) {
            return Action.OVERTAKE;
        }

        // Có thể vượt đèn đỏ
        return Action.MOVE_FORWARD;
    }
}
