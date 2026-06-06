package com.myteam.traffic.behavior.bt;

import com.myteam.traffic.behavior.common.Action;
import com.myteam.traffic.context.RoadContext;
import com.myteam.traffic.vehicle.Vehicle;

public class LeafNodes {

    public static class HasRedLightAhead implements BTNode {
        @Override
        public Action evaluate(Vehicle v, RoadContext ctx) {
            return ctx.hasRedLightAhead() ? Action.STOP : null;
        }
    }

    public static class IsTooCloseToFront implements BTNode {
        @Override
        public Action evaluate(Vehicle v, RoadContext ctx) {
            return ctx.isCollisionImminent() ? Action.SLOW_DOWN : null;
        }
    }

    public static class HasEmergencyNearby implements BTNode {
        @Override
        public Action evaluate(Vehicle v, RoadContext ctx) {
            return ctx.hasEmergencyNearby() ? Action.CHANGE_LANE : null;
        }
    }

    public static class MoveForward implements BTNode {
        @Override
        public Action evaluate(Vehicle v, RoadContext ctx) {
            return Action.MOVE_FORWARD;
        }
    }
}
