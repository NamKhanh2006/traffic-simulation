package com.myteam.traffic.behavior;

import com.myteam.traffic.behavior.common.Action;
import com.myteam.traffic.context.RoadContext;
import com.myteam.traffic.vehicle.TravelMode;
import com.myteam.traffic.vehicle.Vehicle;

public class NormalDriver implements DriverBehavior {

    @Override
    public Action decideAction(Vehicle v, RoadContext context) {

        // 1. TRONG GIAO LỘ: Không bao giờ kẹt!
        if (v.getTravelMode() == TravelMode.ON_INTERSECTION_PATH) {
            for (Vehicle other : context.getNearbyVehicles()) {
                if (other != v && other.getTravelMode() == TravelMode.ON_INTERSECTION_PATH) {
                    double dist = v.getPosition().distanceTo(other.getPosition());

                    // Nếu 2 xe quá gần nhau (< 30m)
                    if (dist < 30.0) {
                        // AI ĐI ÍT HƠN THÌ PHẢI NHƯỜNG (PHANH LẠI)
                        // Xe nào đã đi vào sâu hơn (pathProgress lớn hơn) thì được phóng tiếp
                        if (v.getPathProgress() < other.getPathProgress()) {
                            return Action.STOP;
                        }
                    }
                }
            }
            // Không có ai cản trở thì cắm đầu chạy thoát khỏi giao lộ
            return Action.ACCELERATE;
        }

        // 2. TRÊN ĐƯỜNG THẲNG
        Vehicle front = context.getNearestFrontVehicle();

        if (front != null) {
            double dist = v.getPosition().distanceTo(front.getPosition());

            if (dist < 35.0) return Action.STOP; // Sắp đâm thì phanh

            // Lách lane nếu xe trước đi chậm (điều kiện an toàn do TrafficController lo)
            if (dist < 60.0 && front.getSpeed() < v.getSpeed()) {
                return Action.OVERTAKE;
            }

            if (dist < 80.0) return Action.SLOW_DOWN; // Giảm tốc từ xa
        }

        // 3. Đèn đỏ (Chỉ dừng khi sắp vào giao lộ)
        if (context.hasRedLightAhead()) {
            return Action.STOP;
        }

        // 4. Đường trống -> Đạp ga
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