package com.myteam.traffic.model.infrastructure;

import java.util.*;
import com.myteam.traffic.model.policy.*;

/**
 * Lớp Lane đại diện cho một làn đường cụ thể trong một đoạn đường (RoadSegment).
 * 
 * <p>Lớp này chứa các thông tin về định danh, kích thước vật lý, và đặc biệt là 
 * các chính sách giao thông áp dụng riêng cho làn đó (loại xe, hướng di chuyển, 
 * vạch kẻ đường và giới hạn tốc độ).</p>
 */
public class Lane {

    /** Các loại vạch kẻ đường ngăn cách giữa các làn. */
    public enum MarkingType {
        NONE,               // Không có vạch
        DASHED,             // Vạch nét đứt (cho phép đè vạch/chuyển làn)
        SOLID,              // Vạch nét liền đơn (cấm đè vạch)
        DOUBLE_SOLID,       // Vạch nét liền đôi (thường dùng phân chia hai chiều đi)
        YELLOW_SOLID,       // Vạch vàng liền (cấm dừng đỗ hoặc phân chiều tùy quốc gia)
        /** Vạch kép: Bên trái nét đứt, bên phải nét liền (Nhìn từ phía xe chạy) */
        LEFT_DASHED_RIGHT_SOLID, 
        /** Vạch kép: Bên trái nét liền, bên phải nét đứt */
        LEFT_SOLID_RIGHT_DASHED 
    }

    /** Danh mục các loại phương tiện tham gia giao thông. */
    public enum VehicleCategory { CAR, MOTORBIKE, BUS, BICYCLE, EMERGENCY, TRUCK }

    /** Hướng di chuyển của làn so với hướng vector của RoadSegment. */
    public enum Direction { FORWARD, BACKWARD }

    /** Các hướng di chuyển được phép tại nút giao từ làn này. */
    public enum Movement { STRAIGHT, LEFT, RIGHT, U_TURN }

    // ── Thuộc tính cơ bản ──────────────────────────────────────
    private final int index;            // Số thứ tự làn (thường từ trái sang phải)
    private Direction direction;        // Hướng di chuyển
    private double width;               // Chiều rộng làn (mét)
    private SpeedPolicy customSpeedPolicy; // Chính sách tốc độ riêng (nếu có)

    // ── Ràng buộc và Quy tắc ──────────────────────────────────
    private final Set<VehicleCategory> allowedVehicles; // Các loại xe được phép đi vào
    private final Set<Movement> allowedMovements;       // Các hướng được phép rẽ/đi
    private MarkingType leftMarking;                    // Vạch kẻ bên trái làn
    private MarkingType rightMarking;                   // Vạch kẻ bên phải làn

    /**
     * Khởi tạo một làn đường với đầy đủ các quy tắc giao thông.
     * 
     * @param index Chỉ số của làn (không được âm)
     * @param direction Hướng di chuyển (Forward/Backward)
     * @param width Chiều rộng (phải lớn hơn 0)
     * @param vehicles Tập hợp các loại xe được phép (null = không xe nào)
     * @param movements Tập hợp các hướng di chuyển (null = không hướng nào)
     * @param leftMarking Loại vạch kẻ bên trái
     * @param rightMarking Loại vạch kẻ bên phải
     */
    public Lane(int index, Direction direction, double width,
                Set<VehicleCategory> vehicles, Set<Movement> movements,
                MarkingType leftMarking, MarkingType rightMarking) {

        if (index < 0) throw new IllegalArgumentException("Index làn không được âm");
        if (width <= 0) throw new IllegalArgumentException("Chiều rộng phải > 0");

        this.index = index;
        this.direction = direction;
        this.width = width;
        this.leftMarking = (leftMarking == null) ? MarkingType.NONE : leftMarking;
        this.rightMarking = (rightMarking == null) ? MarkingType.NONE : rightMarking;

        // Sử dụng EnumSet để tối ưu bộ nhớ và tốc độ truy vấn tập hợp
        this.allowedVehicles = (vehicles == null || vehicles.isEmpty())
                ? EnumSet.noneOf(VehicleCategory.class) : EnumSet.copyOf(vehicles);
        this.allowedMovements = (movements == null || movements.isEmpty())
                ? EnumSet.noneOf(Movement.class) : EnumSet.copyOf(movements);
    }

    /**
     * Kiểm tra xe có quyền ưu tiên đặc biệt hay không.
     * Xe cứu thương/cứu hỏa đang làm nhiệm vụ có thể lờ đi các quy tắc vạch kẻ đường.
     */
    private boolean isPriorityPass(VehicleCategory cat, boolean isOnDuty) {
        return cat == VehicleCategory.EMERGENCY && isOnDuty;
    }

    /**
     * Kiểm tra xem phương tiện có được phép đè vạch bên TRÁI để chuyển làn hay không.
     * 
     * @param cat Loại xe đang xét
     * @param isOnDuty Trạng thái làm nhiệm vụ (đối với xe ưu tiên)
     * @return true nếu có thể băng qua vạch bên trái
     */
    public boolean canCrossLeft(VehicleCategory cat, boolean isOnDuty) {
        if (isPriorityPass(cat, isOnDuty)) return true;
        
        return switch (leftMarking) {
            case DASHED -> true;
            // Nếu là vạch kép, chỉ cho phép băng qua nếu phần nét đứt nằm ở phía làn này
            case LEFT_DASHED_RIGHT_SOLID -> true;
            default -> false;
        };
    }

    /**
     * Kiểm tra xem phương tiện có được phép đè vạch bên PHẢI để chuyển làn hay không.
     */
    public boolean canCrossRight(VehicleCategory cat, boolean isOnDuty) {
        if (isPriorityPass(cat, isOnDuty)) return true;
        
        return switch (rightMarking) {
            case DASHED -> true;
            // Nếu là vạch kép, chỉ cho phép băng qua nếu phần nét đứt nằm ở phía làn này
            case LEFT_SOLID_RIGHT_DASHED -> true;
            default -> false;
        };
    }

    /** Kiểm tra loại xe này có được phép lưu thông trên làn này không. */
    public boolean allowsVehicle(VehicleCategory cat) {
        return allowedVehicles.contains(cat);
    }

    /** Kiểm tra hướng di chuyển này có hợp lệ khi đi hết làn này không. */
    public boolean allowsMovement(Movement movement) {
        return allowedMovements.contains(movement);
    }

    // ── Getters & Setters ─────────────────────────────────────

    public int getIndex() { return index; }
    public Direction getDirection() { return direction; }
    public double getWidth() { return width; }

    /** Trả về danh sách xe được phép (Chỉ đọc). */
    public Set<VehicleCategory> getAllowedVehicles() {
        return Collections.unmodifiableSet(allowedVehicles);
    }

    /** Trả về danh sách hướng di chuyển (Chỉ đọc). */
    public Set<Movement> getAllowedMovements() {
        return Collections.unmodifiableSet(allowedMovements);
    }

    public SpeedPolicy getCustomSpeedPolicy() { return customSpeedPolicy; }
    
    /** Thiết lập giới hạn tốc độ riêng cho làn này (VD: Làn sát con lươn chạy nhanh hơn). */
    public void setCustomSpeedPolicy(SpeedPolicy policy) { this.customSpeedPolicy = policy; }

    /**
     * Trả về thông tin chi tiết của làn đường dưới dạng chuỗi.
     */
    @Override
    public String toString() {
        String speedStr = (customSpeedPolicy != null) ? customSpeedPolicy.getLimit() + "km/h" : "Default";
        return String.format(
                "Lane[%d] | %s | Width: %.1f | Speed: %s | L-Marking: %s | R-Marking: %s",
                index, direction, width, speedStr, leftMarking, rightMarking
        );
    }
}