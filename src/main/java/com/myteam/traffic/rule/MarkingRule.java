package com.myteam.traffic.rule;

import com.myteam.traffic.behavior.common.Action;
import com.myteam.traffic.context.RoadContext;
import com.myteam.traffic.model.infrastructure.Lane;
import com.myteam.traffic.vehicle.Vehicle;
import com.myteam.traffic.vehicle.VehicleType;

/**
 * Kiểm tra phương tiện có được phép thực hiện action dựa trên vạch kẻ đường
 * của làn đang đi.
 *
 * Chỉ các action liên quan đến việc vượt qua ranh giới làn mới bị kiểm tra:
 *   CHANGE_LANE — đổi sang làn kề (trái hoặc phải)
 *   OVERTAKE    — vượt xe, thường qua làn trái
 *   U_TURN      — quay đầu, luôn cắt qua vạch tim đường (bên trái)
 *
 * Cách xác định xe được phép hay không:
 *   Dữ liệu vạch kẻ đã lưu sẵn trong Lane (leftMarking, rightMarking).
 *   Thay vì tính toán hình học phức tạp về vị trí tương lai của xe,
 *   rule đọc trực tiếp Lane.canCrossLeft() / canCrossRight() —
 *   thông tin này chính xác và luôn có sẵn.
 *
 * Xe khẩn cấp đang làm nhiệm vụ (isEmergency() == true) được miễn trừ
 * toàn bộ rule này thông qua appliesTo().
 */
public class MarkingRule implements TrafficRule {

    // ── Constructor ───────────────────────────────────────────
    // Không cần tham số — rule áp dụng toàn cục, miễn trừ qua appliesTo()
    public MarkingRule() {}

    // =========================================================
    // appliesTo — xác định đối tượng bị ràng buộc
    // =========================================================

    /**
     * Xe khẩn cấp (cứu thương, cứu hỏa, cảnh sát) được miễn trừ.
     * Chúng được phép vượt bất kỳ vạch nào khi đang làm nhiệm vụ.
     */
    @Override
    public boolean appliesTo(Vehicle v) {
        return !v.isEmergency();
    }

    // =========================================================
    // isAllowed — logic kiểm tra chính
    // =========================================================

    /**
     * Kiểm tra action có vi phạm vạch kẻ đường không.
     *
     * Với các action KHÔNG liên quan đến vượt ranh giới làn
     * (MOVE_FORWARD, STOP, HONK...) → luôn cho phép, không cần kiểm tra.
     *
     * Với CHANGE_LANE / OVERTAKE / U_TURN:
     *   - Lấy làn hiện tại từ context
     *   - Kiểm tra vạch trái và phải có cho phép vượt không
     *   - Nếu ít nhất một phía được phép → cho phép action
     *
     * Tại sao kiểm tra "ít nhất một phía" thay vì biết chính xác phía nào?
     * → Behavior chưa truyền thông tin "đổi sang trái hay phải" vào Action.
     *   Khi hệ thống mở rộng hơn (Action mang thêm dữ liệu hướng),
     *   chỉ cần sửa phương thức canCross() bên dưới mà không đụng logic khác.
     */
    @Override
    public boolean isAllowed(Vehicle v, Action a, RoadContext ctx) {
        if (!isLaneCrossingAction(a)) return true;

        Lane lane = ctx.getCurrentLane();
        if (lane == null) return true; // Chưa có thông tin làn → cho qua

        boolean allowed = canCross(lane, v, a);

        if (!allowed) {
            System.out.printf(
                "[MARKING_RULE] BLOCKED: %s không được %s — vạch %s/%s%n",
                v.getType(), a,
                lane.getLeftMarking(), lane.getRightMarking()
            );
        }
        return allowed;
    }

    @Override
    public int getPriority() {
        return 40;
    }

    // =========================================================
    // Helpers
    // =========================================================

    /**
     * Những action nào thực sự đòi hỏi xe phải vượt qua ranh giới làn?
     *
     *   CHANGE_LANE — rõ ràng: xe dịch sang làn khác
     *   OVERTAKE    — vượt xe phía trước, thường phải sang làn trái
     *   U_TURN      — quay đầu, luôn cắt qua vạch tim đường
     *
     * MOVE_FORWARD, STOP, SLOW_DOWN, ACCELERATE, HONK, TURN_LEFT,
     * TURN_RIGHT — đều không vượt ranh giới làn → không cần kiểm tra vạch.
     */
    private boolean isLaneCrossingAction(Action a) {
        return switch (a) {
            case CHANGE_LANE, OVERTAKE, U_TURN -> true;
            default -> false;
        };
    }

    /**
     * Kiểm tra làn {@code lane} có cho phép xe vượt ranh giới không.
     *
     * U_TURN luôn cắt vạch tim đường → chỉ kiểm tra canCrossLeft.
     * CHANGE_LANE / OVERTAKE → kiểm tra cả hai phía (trái hoặc phải).
     *
     * canCrossLeft/Right nhận VehicleCategory và isOnDuty.
     * Với xe thường (isEmergency = false), isOnDuty = false.
     */
    private boolean canCross(Lane lane, Vehicle v, Action a) {
        Lane.VehicleCategory cat = toVehicleCategory(v.getType());
        boolean              onDuty = v.isEmergency();

        if (a == Action.U_TURN) {
            // Quay đầu chỉ cắt vạch bên trái (vạch tim đường)
            return lane.canCrossLeft(cat, onDuty);
        }

        // CHANGE_LANE / OVERTAKE: đủ nếu ít nhất một phía cho phép
        return lane.canCrossLeft(cat, onDuty)
            || lane.canCrossRight(cat, onDuty);
    }

    /**
     * Ánh xạ từ VehicleType (tầng vehicle) sang Lane.VehicleCategory
     * (tầng infrastructure).
     *
     * Hai enum này tách biệt vì mỗi tầng có trách nhiệm khác nhau:
     *   VehicleType  — phân loại xe theo nghiệp vụ (Car, Motorbike...)
     *   VehicleCategory — phân loại xe theo quy tắc làn đường
     * MarkingRule là nơi hợp lý nhất để thực hiện ánh xạ này.
     */
    private Lane.VehicleCategory toVehicleCategory(VehicleType type) {
        if (type == null) return Lane.VehicleCategory.CAR;
        return switch (type) {
            case CAR       -> Lane.VehicleCategory.CAR;
            case MOTORBIKE -> Lane.VehicleCategory.MOTORBIKE;
            case BICYCLE   -> Lane.VehicleCategory.BICYCLE;
            case EMERGENCY -> Lane.VehicleCategory.EMERGENCY;
            default        -> Lane.VehicleCategory.CAR;
        };
    }
}