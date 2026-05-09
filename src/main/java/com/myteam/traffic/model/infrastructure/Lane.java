package com.myteam.traffic.model.infrastructure;

import java.util.*;
import com.myteam.traffic.model.policy.*;

/**
 * Lớp Lane đại diện cho một làn đường vật lý trong hệ thống hạ tầng giao thông.
 * 
 * <p>Mỗi làn đường là một đơn vị điều hướng cơ bản, chứa các quy tắc về:
 * <ul>
 *   <li><b>Hình học:</b> Chỉ số làn, chiều rộng và hướng di chuyển.</li>
 *   <li><b>Luật lệ:</b> Loại xe được phép, các hướng rẽ tại nút giao.</li>
 *   <li><b>Vạch kẻ:</b> Quy định khả năng chuyển làn (đè vạch) sang trái hoặc phải.</li>
 *   <li><b>Chính sách:</b> Giới hạn tốc độ riêng biệt cho từng làn.</li>
 * </ul>
 */
public class Lane {

    /** Các kiểu vạch kẻ đường xác định khả năng tương tác giữa các làn kề nhau. */
    public enum MarkingType {
        NONE,           // Không có vạch (thường dùng ở trong nút giao)
        DASHED,         // Vạch đứt đoạn (cho phép chuyển làn tự do)
        SOLID,          // Vạch liền đơn (cấm chuyển làn)
        DOUBLE_SOLID,   // Vạch liền đôi (cấm đè vạch tuyệt đối)
        YELLOW_SOLID,   // Vạch vàng liền (thường phân chia chiều đường)
        /** Vạch kép: Bên trái nét đứt, bên phải nét liền. Xe từ làn này có thể sang trái. */
        LEFT_DASHED_RIGHT_SOLID, 
        /** Vạch kép: Bên trái nét liền, bên phải nét đứt. Xe từ làn này có thể sang phải. */
        LEFT_SOLID_RIGHT_DASHED 
    }

    /** Phân loại phương tiện để áp dụng quy tắc làn ưu tiên hoặc làn cấm. */
    public enum VehicleCategory { CAR, MOTORBIKE, BUS, BICYCLE, EMERGENCY, TRUCK }

    /** Hướng lưu thông của làn so với hướng vector của đoạn đường (RoadSegment). */
    public enum Direction { FORWARD, BACKWARD }

    /** Các hướng di chuyển hợp lệ khi phương tiện đi đến cuối làn này (thường tại nút giao). */
    public enum Movement { STRAIGHT, LEFT, RIGHT, U_TURN }

    // ── Fields ───────────────────────────────────────────────

    private final int index;                // Chỉ số thứ tự của làn (tính từ trái sang phải hoặc ngược lại)
    private Direction direction;            // Hướng di chuyển chính của làn
    private double width;                   // Chiều rộng làn đường (mét)
    private SpeedPolicy customSpeedPolicy;  // Chính sách tốc độ đặc thù cho làn này

    private final Set<VehicleCategory> allowedVehicles; // Tập hợp các xe được phép chạy
    private final Set<Movement>        allowedMovements; // Các hướng rẽ được phép từ làn này
    private MarkingType leftMarking;        // Vạch kẻ ngăn cách phía bên trái làn
    private MarkingType rightMarking;       // Vạch kẻ ngăn cách phía bên phải làn

    // ── Constructor ──────────────────────────────────────────

    /**
     * Khởi tạo một đối tượng Lane với đầy đủ các thuộc tính cấu hình.
     * 
     * @param index Chỉ số của làn (không được phép âm).
     * @param direction Hướng lưu thông chính.
     * @param width Chiều rộng vật lý (phải > 0).
     * @param vehicles Danh sách xe được phép (null hoặc trống sẽ tạo tập hợp rỗng).
     * @param movements Các hướng di chuyển cho phép (null hoặc trống sẽ tạo tập hợp rỗng).
     * @param leftMarking Vạch kẻ phía bên trái làn.
     * @param rightMarking Vạch kẻ phía bên phải làn.
     */
    public Lane(int index, Direction direction, double width,
                Set<VehicleCategory> vehicles, Set<Movement> movements,
                MarkingType leftMarking, MarkingType rightMarking) {

        if (index < 0) throw new IllegalArgumentException("Index làn không được âm");
        if (width <= 0) throw new IllegalArgumentException("Chiều rộng phải > 0");

        this.index        = index;
        this.direction    = direction;
        this.width        = width;
        this.leftMarking  = (leftMarking  == null) ? MarkingType.NONE : leftMarking;
        this.rightMarking = (rightMarking == null) ? MarkingType.NONE : rightMarking;

        // Sử dụng EnumSet để tối ưu hiệu năng và bộ nhớ so với HashSet thông thường
        this.allowedVehicles = (vehicles  == null || vehicles.isEmpty())
                ? EnumSet.noneOf(VehicleCategory.class) : EnumSet.copyOf(vehicles);
        this.allowedMovements = (movements == null || movements.isEmpty())
                ? EnumSet.noneOf(Movement.class) : EnumSet.copyOf(movements);
    }

    // ── Quyền ưu tiên ────────────────────────────────────────

    /**
     * Xác định xem một phương tiện có quyền ưu tiên đặc biệt (Emergency Pass) hay không.
     * 
     * @param cat Loại xe cần kiểm tra.
     * @param isOnDuty Trạng thái xe đang thực hiện nhiệm vụ khẩn cấp (có còi/đèn).
     * @return true nếu phương tiện được phép bỏ qua các ràng buộc giao thông thông thường.
     */
    private boolean isPriorityPass(VehicleCategory cat, boolean isOnDuty) {
        return cat == VehicleCategory.EMERGENCY && isOnDuty;
    }

    // ── Kiểm tra vạch kẻ ─────────────────────────────────────

    /**
     * Kiểm tra khả năng đè vạch bên trái để thực hiện hành vi chuyển làn hoặc rẽ.
     * 
     * @param cat Loại xe thực hiện hành vi.
     * @param isOnDuty Xe có đang làm nhiệm vụ khẩn cấp hay không.
     * @return true nếu hành vi băng qua vạch bên trái là hợp lệ.
     */
    public boolean canCrossLeft(VehicleCategory cat, boolean isOnDuty) {
        if (isPriorityPass(cat, isOnDuty)) return true;
        return switch (leftMarking) {
            case DASHED                 -> true;
            case LEFT_DASHED_RIGHT_SOLID -> true; // Phần nét đứt nằm bên phía làn này
            default                     -> false;
        };
    }

    /**
     * Kiểm tra khả năng đè vạch bên phải để thực hiện hành vi chuyển làn.
     * 
     * @param cat Loại xe thực hiện hành vi.
     * @param isOnDuty Xe có đang làm nhiệm vụ khẩn cấp hay không.
     * @return true nếu hành vi băng qua vạch bên phải là hợp lệ.
     */
    public boolean canCrossRight(VehicleCategory cat, boolean isOnDuty) {
        if (isPriorityPass(cat, isOnDuty)) return true;
        return switch (rightMarking) {
            case DASHED                  -> true;
            case LEFT_SOLID_RIGHT_DASHED -> true; // Phần nét đứt nằm bên phía làn này
            default                      -> false;
        };
    }

    // ── Kiểm tra xe và hướng đi ──────────────────────────────

    /**
     * Kiểm tra xem một loại phương tiện cụ thể có được phép đi vào làn này hay không.
     */
    public boolean allowsVehicle(VehicleCategory cat, boolean isOnDuty) {
        if (isPriorityPass(cat, isOnDuty)) return true;
        return allowedVehicles.contains(cat);
    }

    /**
     * Kiểm tra xem một hướng di chuyển (thẳng, rẽ trái, rẽ phải...) có được phép từ làn này hay không.
     */
    public boolean allowsMovement(Movement movement, VehicleCategory cat, boolean isOnDuty) {
        if (isPriorityPass(cat, isOnDuty)) return true;
        return allowedMovements.contains(movement);
    }

    // ── Getters & Setters ─────────────────────────────────────

    public int      getIndex()     { return index;     }
    public Direction getDirection() { return direction; }
    public double    getWidth()     { return width;     }

    public MarkingType getLeftMarking()  { return leftMarking;  }
    public MarkingType getRightMarking() { return rightMarking; }

    /** Trả về danh sách xe cho phép dưới dạng Read-Only để bảo vệ dữ liệu nội bộ. */
    public Set<VehicleCategory> getAllowedVehicles()  { return Collections.unmodifiableSet(allowedVehicles);  }
    
    /** Trả về các hướng di chuyển cho phép dưới dạng Read-Only. */
    public Set<Movement>        getAllowedMovements() { return Collections.unmodifiableSet(allowedMovements); }

    public SpeedPolicy getCustomSpeedPolicy()              { return customSpeedPolicy;  }
    public void setCustomSpeedPolicy(SpeedPolicy policy)   { this.customSpeedPolicy = policy; }

    public void setDirection(Direction direction)          { this.direction    = direction;   }
    public void setWidth(double width)                      { this.width        = width;       }
    public void setLeftMarking(MarkingType m)               { this.leftMarking  = m;           }
    public void setRightMarking(MarkingType m)              { this.rightMarking = m;           }

    /**
     * Trả về chuỗi mô tả tóm tắt trạng thái làn đường để phục vụ logging và debug.
     */
    @Override
    public String toString() {
        String speedStr = (customSpeedPolicy != null)
                ? customSpeedPolicy.getLimit() + "km/h" : "Default";
        return String.format(
                "Lane[%d] | %s | Width: %.1f | Speed: %s | L: %s | R: %s",
                index, direction, width, speedStr, leftMarking, rightMarking);
    }
}