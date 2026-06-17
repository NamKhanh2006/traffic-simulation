package com.myteam.traffic.behavior;

import com.myteam.traffic.behavior.common.Action;
import com.myteam.traffic.context.RoadContext;
import com.myteam.traffic.vehicle.TravelMode;
import com.myteam.traffic.vehicle.Vehicle;

public class NormalDriver implements DriverBehavior {

    @Override
    public Action decideAction(Vehicle v, RoadContext context) {

        // 1. TRONG GIAO LỘ: Xóa bỏ deadlock, cắm đầu chạy thoát
        if (v.getTravelMode() == TravelMode.ON_INTERSECTION_PATH) {
            for (Vehicle other : context.getNearbyVehicles()) {
                if (other != v && other.getTravelMode() == TravelMode.ON_INTERSECTION_PATH) {

                    // CHỈ phanh lại nếu xe kia đi CÙNG MỘT ĐƯỜNG CONG (Cùng làn rẽ) với mình
                    if (other.getActivePath() == v.getActivePath()) {
                        double myProg = v.getPathProgress();
                        double otherProg = other.getPathProgress();

                        // Nếu xe kia đi phía trước và cách mình < 35 đơn vị -> Phanh
                        if (otherProg > myProg && (otherProg - myProg) < 35.0) {
                            return Action.STOP;
                        }
                    } else {
                        // NẾU 2 XE CẮT CHÉO NHAU:
                        // Chỉ phanh khi xe kia ĐÃ CHẮN NGANG SÁT MŨI (< 15 đơn vị)
                        double dist = v.getPosition().distanceTo(other.getPosition());
                        if (dist < 15.0 && v.getPosition().isAheadInDirection(v.getDirection(), other.getPosition())) {
                            return Action.STOP;
                        }
                    }
                }
            }
            return Action.ACCELERATE;
        }

        // 2. TRÊN ĐƯỜNG THẲNG
        Vehicle front = context.getNearestFrontVehicle();

        if (front != null) {
            double dist = v.getPosition().distanceTo(front.getPosition());

            // Sắp đâm đít -> Phanh khẩn cấp
            if (dist < 35.0) return Action.STOP;

            // Lách lane vượt xe rùa bò
            if (dist < 60.0 && front.getSpeed() < v.getSpeed()) {
                return Action.OVERTAKE;
            }

            // Rà phanh từ xa
            if (dist < 80.0) return Action.SLOW_DOWN;
        }

        // 3. Đèn đỏ
        if (context.hasRedLightAhead()) {
            return Action.STOP;
        }

        // 4. Đường trống -> Tăng tốc
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