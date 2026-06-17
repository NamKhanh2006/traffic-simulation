package com.myteam.traffic.rule;

import com.myteam.traffic.behavior.common.Action;
import com.myteam.traffic.context.RoadContext;
import com.myteam.traffic.light.TrafficLightState;
import com.myteam.traffic.vehicle.Vehicle;

/**
 * Kiểm tra phương tiện có tuân thủ tín hiệu đèn giao thông không.
 *
 * Bảng hành vi theo màu đèn:
 *
 * ┌─────────┬────────────────────────────────────────────────────┐
 * │  ĐÈN    │  ACTION                                            │
 * ├─────────┼────────────────────────────────────────────────────┤
 * │  ĐỎ    │  ✓ STOP, SLOW_DOWN, HONK                           │
 * │         │  ✗ Tất cả còn lại (MOVE_FORWARD, OVERTAKE, ...)   │
 * ├─────────┼────────────────────────────────────────────────────┤
 * │  VÀNG  │  ✓ STOP, SLOW_DOWN, HONK (luôn luôn)              │
 * │         │  ✗ ACCELERATE, OVERTAKE (không bao giờ)           │
 * │         │  ? Còn lại: chỉ được nếu canProceedSafely()       │
 * ├─────────┼────────────────────────────────────────────────────┤
 * │  XANH  │  ✓ Tất cả                                         │
 * └─────────┴────────────────────────────────────────────────────┘
 *
 * Tại sao đèn vàng phức tạp hơn đèn đỏ?
 * → Trong thực tế, nếu xe đã quá gần vạch dừng, phanh gấp lại nguy hiểm
 *   hơn là tiếp tục đi. canProceedSafely() mô phỏng tình huống này:
 *   xe được qua nếu đèn xanh VÀ không có gì cản phía trước.
 *
 * Xe khẩn cấp đang làm nhiệm vụ được miễn trừ toàn bộ rule này.
 */
public class SignalRule implements TrafficRule {

    // ── Constructor ───────────────────────────────────────────
    public SignalRule() {}

    // =========================================================
    // appliesTo — xác định đối tượng bị ràng buộc
    // =========================================================

    /**
     * Xe khẩn cấp (cứu thương, cứu hỏa, cảnh sát) được phép vượt
     * đèn đỏ khi đang làm nhiệm vụ.
     * EmergencyDriver.isEmergency() trả true → rule này bị bỏ qua.
     */
    @Override
    public boolean appliesTo(Vehicle v) {
        return !v.isEmergency();
    }

    // =========================================================
    // isAllowed — logic kiểm tra chính
    // =========================================================

    /**
     * Ủy quyền sang ba phương thức riêng biệt theo màu đèn.
     * Mỗi màu đèn có tập luật khác nhau — tách riêng giúp dễ đọc
     * và dễ sửa nếu luật giao thông thay đổi sau này.
     */
    @Override
    public boolean isAllowed(Vehicle v, Action a, RoadContext ctx) {
        TrafficLightState light = ctx.getLightState();

        boolean allowed = switch (light) {
            case RED    -> isAllowedOnRed(a);
            case YELLOW -> isAllowedOnYellow(a, v, ctx);
            case GREEN  -> true;
        };

        if (!allowed) {
            System.out.printf(
                "[SIGNAL_RULE] BLOCKED: %s không được %s — đèn %s%n",
                v.getType(), a, light
            );
        }
        return allowed;
    }

    @Override
    public int getPriority() {
        return 100;
    }

    // =========================================================
    // Logic theo từng màu đèn
    // =========================================================

    /**
     * Đèn ĐỎ — xe phải đứng yên hoàn toàn.
     *
     * Được phép:
     *   STOP      — dừng lại (hành động đúng nhất)
     *   SLOW_DOWN — đang giảm tốc để dừng
     *   HONK      — bấm còi (không di chuyển, không vi phạm)
     *
     * Bị cấm: tất cả action còn lại vì đều liên quan đến di chuyển.
     */
    private boolean isAllowedOnRed(Action a) {
        return switch (a) {
            case STOP, SLOW_DOWN, HONK, YIELD -> true;
            default -> false;
        };
    }

    /**
     * Đèn VÀNG — khuyến khích dừng, nhưng không cứng nhắc như đèn đỏ.
     *
     * Luôn được phép:
     *   STOP, SLOW_DOWN, HONK — an toàn và phù hợp với đèn vàng
     *
     * Không bao giờ được phép:
     *   ACCELERATE — tăng tốc khi sắp chuyển đỏ là vi phạm rõ ràng
     *   OVERTAKE   — vượt xe khi đèn sắp đỏ quá nguy hiểm
     *
     * Các action còn lại (MOVE_FORWARD, CHANGE_LANE, TURN_LEFT, TURN_RIGHT...):
     *   Cho phép NẾU canProceedSafely() == true.
     *   Mô phỏng tình huống xe đã quá gần nút giao, dừng lại nguy hiểm hơn đi.
     *
     * @param v   Xe cần kiểm tra
     * @param ctx Context hiện tại (cần để gọi canProceedSafely)
     */
    private boolean isAllowedOnYellow(Action a, Vehicle v, RoadContext ctx) {
        return switch (a) {
            // Luôn an toàn khi đèn vàng
            case STOP, SLOW_DOWN, HONK, YIELD -> true;

            // Không bao giờ được phép khi đèn vàng
            case ACCELERATE, OVERTAKE  -> false;

            // Các action còn lại: chỉ được nếu đủ điều kiện an toàn
            // canProceedSafely() = đèn xanh VÀ không có gì cản phía trước
            // Ở đây đèn đang vàng nên điều kiện đèn xanh sẽ false,
            // nhưng nếu xe đang trong intersection thì vẫn nên tiếp tục
            default -> ctx.canProceedSafely(v);
        };
    }
}

