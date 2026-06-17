package com.myteam.traffic.behavior;

import com.myteam.traffic.behavior.common.Action;
import com.myteam.traffic.context.RoadContext;
import com.myteam.traffic.vehicle.Vehicle;
import com.myteam.traffic.behavior.common.DistanceKeeping;
import com.myteam.traffic.vehicle.TravelMode;

/**
 * Driver behaviour for emergency vehicles (ambulance, fire truck, police).
 *
 * Decision priority (highest → lowest):
 * 1. Siren not on yet → HONK (activate siren first)
 * 2. Vehicle ahead → OVERTAKE
 * 3. Default → MOVE_FORWARD (high-speed transit)
 *
 * Emergency vehicles bypass most traffic rules — the TrafficController
 * checks isEmergency() and skips rules whose affectedVehicles list does
 * not include emergency vehicles.
 *
 * Rejection fallback:
 * Emergency vehicles are rarely rejected, but if they are (e.g. physical
 * impossibility) they fall back to SLOW_DOWN rather than STOP, to keep
 * moving as much as possible.
 */
public class EmergencyDriver implements DriverBehavior {
    /*
     * @Override
     * public Action decideAction(Vehicle v, RoadContext context) {
     * if (!v.isSirenOn()) {
     * return Action.HONK;
     * }
     * Vehicle front = context.getNearestFrontVehicle();
     * // Dừng khẩn cấp nếu va chạm sắp xảy ra
     * if (DistanceKeeping.isImminentCollision(v, front)) {
     * return Action.STOP;
     * }
     * if (front != null) {
     * return Action.OVERTAKE;
     * }
     * // Tăng tốc đến tốc độ tối đa
     * if (v.getSpeed() < v.getMaxSpeed()) {
     * return Action.ACCELERATE;
     * }
     * return Action.MOVE_FORWARD;
     * }
     */

    @Override
    public Action handleRejection(Vehicle v, RoadContext context, Action rejected) {
        // Emergency vehicles keep moving even when rejected – slow down rather than
        // stop
        return Action.SLOW_DOWN;
    }

    /**
     * Signals the TrafficController to skip non-applicable rules.
     * Rules that do not include emergency vehicles in their affectedVehicles
     * list will be ignored automatically.
     */
    @Override
    public boolean isEmergency() {
        return true;
    }

    @Override
    public Action decideAction(Vehicle v, RoadContext context) {
        if (!v.isSirenOn())
            return Action.HONK;

        if (v.getTravelMode() == TravelMode.ON_INTERSECTION_PATH) {
            Vehicle front = context.getNearestFrontVehicle();
            if (front != null && DistanceKeeping.isImminentCollision(v, front))
                return Action.STOP;
            if (Math.random() < 0.02)
                return Action.HONK;
            return Action.ACCELERATE;
        }

        Vehicle front = context.getNearestFrontVehicle();
        if (DistanceKeeping.isImminentCollision(v, front))
            return Action.STOP;

        if (front != null) {
            // Xe ưu tiên bóp còi giục dẹp đường khi có xe phía trước cản trở
            double dist = v.getPosition().distanceTo(front.getPosition());
            if (dist < 40.0) {
                if (Math.random() < 0.05) return Action.HONK;
                // Sửa lỗi: Thay vì STOP, xe ưu tiên phải tìm cách lách sang làn khác (OVERTAKE)
                return Action.OVERTAKE;
            } else if (dist < 100.0) {
                if (Math.random() < 0.05) return Action.HONK;
                return Action.OVERTAKE;
            }
        }

        // Thỉnh thoảng vẫn hú còi để cảnh báo từ xa
        if (v.getSpeed() < v.getMaxSpeed()) {
            if (Math.random() < 0.01)
                return Action.HONK;
            return Action.ACCELERATE;
        }

        if (Math.random() < 0.01)
            return Action.HONK;
        return Action.MOVE_FORWARD;
    }
}
