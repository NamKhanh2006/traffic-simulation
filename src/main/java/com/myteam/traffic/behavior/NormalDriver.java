// NormalDriver.java – phiên bản sửa
package com.myteam.traffic.behavior;

import com.myteam.traffic.behavior.common.Action;
import com.myteam.traffic.behavior.common.DistanceKeeping;
import com.myteam.traffic.context.RoadContext;
import com.myteam.traffic.vehicle.TravelMode;
import com.myteam.traffic.vehicle.Vehicle;

public class NormalDriver implements DriverBehavior {

    @Override
    public Action decideAction(Vehicle v, RoadContext context) {
        // 1. Xử lý khi đang trong giao lộ
        if (v.getTravelMode() == TravelMode.ON_INTERSECTION_PATH) {
            Vehicle front = context.getNearestFrontVehicle();
            // Nếu có xe phía trước trên cùng quỹ đạo và TTC nguy hiểm → dừng
            if (front != null && DistanceKeeping.isImminentCollision(v, front)) {
                return Action.STOP;
            }
            // Kiểm tra khoảng cách an toàn theo TTC
            if (front != null && DistanceKeeping.timeToCollision(v, front) < DistanceKeeping.SAFE_TTC) {
                return Action.SLOW_DOWN;
            }
            // Không có cản trở → tăng tốc để thoát giao lộ
            return Action.ACCELERATE;
        }

        // 2. Xử lý trên đường thẳng
        Vehicle front = context.getNearestFrontVehicle();

        // Dừng khẩn cấp nếu TTC quá nhỏ
        if (front != null && DistanceKeeping.isImminentCollision(v, front)) {
            return Action.STOP;
        }
        // Giảm tốc nếu TTC không an toàn
        if (front != null && DistanceKeeping.timeToCollision(v, front) < DistanceKeeping.SAFE_TTC) {
            return Action.SLOW_DOWN;
        }
        // Có xe phía trước nhưng đủ an toàn, và xe trước chậm hơn → thử vượt
        if (front != null && front.getSpeed() < v.getSpeed() && 
            DistanceKeeping.timeToCollision(v, front) > DistanceKeeping.SAFE_TTC + 1.0) {
            return Action.OVERTAKE;
        }

        // 3. Đèn đỏ (chỉ dừng khi sắp vào giao lộ)
        if (context.hasRedLightAhead()) {
            return Action.STOP;
        }

        // 4. Tăng tốc nếu chưa đạt maxSpeed
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
