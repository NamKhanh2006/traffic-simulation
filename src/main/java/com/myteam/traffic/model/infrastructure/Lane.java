package com.myteam.traffic.model.infrastructure;

import java.util.*;

/**
 * Làn đường vật lý — fully immutable.
 * Mọi thay đổi đều tạo object mới qua wither methods.
 */
public final class Lane {

    // ── Enums ─────────────────────────────────────────────────

    public enum MarkingType {
        NONE, DASHED, SOLID, DOUBLE_SOLID, YELLOW_SOLID,
        /** Nét đứt bên trái, nét liền bên phải — xe bên trái vạch được sang. */
        LEFT_DASHED_RIGHT_SOLID,
        /** Nét liền bên trái, nét đứt bên phải — xe bên phải vạch được sang. */
        LEFT_SOLID_RIGHT_DASHED
    }

    public enum VehicleCategory { CAR, MOTORBIKE, BUS, BICYCLE, EMERGENCY, TRUCK }
    public enum Direction        { FORWARD, BACKWARD }
    public enum Movement         { STRAIGHT, LEFT, RIGHT, U_TURN }

    // ── Fields (tất cả final) ─────────────────────────────────

    private final int                  index;
    private final Direction            direction;
    private final double               width;
    private final Set<VehicleCategory> allowedVehicles;
    private final Set<Movement>        allowedMovements;
    private final MarkingType          leftMarking;
    private final MarkingType          rightMarking;

    // ── Constructor ───────────────────────────────────────────

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

        this.allowedVehicles  = (vehicles  == null || vehicles.isEmpty())
                ? EnumSet.noneOf(VehicleCategory.class) : EnumSet.copyOf(vehicles);
        this.allowedMovements = (movements == null || movements.isEmpty())
                ? EnumSet.noneOf(Movement.class) : EnumSet.copyOf(movements);
    }

    // ── Wither Methods ────────────────────────────────────────

    public Lane withWidth(double newWidth) {
        return new Lane(index, direction, newWidth,
                allowedVehicles, allowedMovements, leftMarking, rightMarking);
    }

    public Lane withDirection(Direction newDirection) {
        return new Lane(index, newDirection, width,
                allowedVehicles, allowedMovements, leftMarking, rightMarking);
    }

    public Lane withLeftMarking(MarkingType m) {
        return new Lane(index, direction, width, allowedVehicles, allowedMovements,
                (m == null) ? MarkingType.NONE : m, rightMarking);
    }

    public Lane withRightMarking(MarkingType m) {
        return new Lane(index, direction, width, allowedVehicles, allowedMovements,
                leftMarking, (m == null) ? MarkingType.NONE : m);
    }

    /** Trả về Lane mới với thêm một loại xe được phép. */
    public Lane withAddedVehicle(VehicleCategory cat) {
        Set<VehicleCategory> updated = EnumSet.copyOf(allowedVehicles);
        updated.add(cat);
        return new Lane(index, direction, width,
                updated, allowedMovements, leftMarking, rightMarking);
    }

    /** Trả về Lane mới với một loại xe bị xóa khỏi danh sách cho phép. */
    public Lane withRemovedVehicle(VehicleCategory cat) {
        Set<VehicleCategory> updated = allowedVehicles.isEmpty()
                ? EnumSet.noneOf(VehicleCategory.class) : EnumSet.copyOf(allowedVehicles);
        updated.remove(cat);
        return new Lane(index, direction, width,
                updated, allowedMovements, leftMarking, rightMarking);
    }

    /** Trả về Lane mới với thêm một hướng di chuyển được phép. */
    public Lane withAddedMovement(Movement mvt) {
        Set<Movement> updated = EnumSet.copyOf(allowedMovements);
        updated.add(mvt);
        return new Lane(index, direction, width,
                allowedVehicles, updated, leftMarking, rightMarking);
    }

    /** Trả về Lane mới với một hướng di chuyển bị xóa. */
    public Lane withRemovedMovement(Movement mvt) {
        Set<Movement> updated = allowedMovements.isEmpty()
                ? EnumSet.noneOf(Movement.class) : EnumSet.copyOf(allowedMovements);
        updated.remove(mvt);
        return new Lane(index, direction, width,
                allowedVehicles, updated, leftMarking, rightMarking);
    }

    // ── Logic quyền ưu tiên ───────────────────────────────────

    private boolean isPriorityPass(VehicleCategory cat, boolean isOnDuty) {
        return cat == VehicleCategory.EMERGENCY && isOnDuty;
    }

    // ── Logic vạch kẻ ─────────────────────────────────────────

    /**
     * Kiểm tra xe có thể đổi sang làn bên TRÁI không.
     * leftMarking là vạch nằm bên trái của làn này.
     * LEFT_SOLID_RIGHT_DASHED: nét đứt nằm về phía làn hiện tại → được qua.
     */
    public boolean canCrossLeft(VehicleCategory cat, boolean isOnDuty) {
        if (isPriorityPass(cat, isOnDuty)) return true;
        return switch (leftMarking) {
            case DASHED, LEFT_SOLID_RIGHT_DASHED -> true;
            default                              -> false;
        };
    }

    /**
     * Kiểm tra xe có thể đổi sang làn bên PHẢI không.
     * rightMarking là vạch nằm bên phải của làn này.
     * LEFT_DASHED_RIGHT_SOLID: nét đứt nằm về phía làn hiện tại → được qua.
     */
    public boolean canCrossRight(VehicleCategory cat, boolean isOnDuty) {
        if (isPriorityPass(cat, isOnDuty)) return true;
        return switch (rightMarking) {
            case DASHED, LEFT_DASHED_RIGHT_SOLID -> true;
            default                              -> false;
        };
    }

    /** Kiểm tra loại xe có được phép vào làn này không. */
    public boolean allowsVehicle(VehicleCategory cat, boolean isOnDuty) {
        if (isPriorityPass(cat, isOnDuty)) return true;
        return allowedVehicles.contains(cat);
    }

    /** Kiểm tra hướng di chuyển có được phép từ làn này không. */
    public boolean allowsMovement(Movement mvt, VehicleCategory cat, boolean isOnDuty) {
        if (isPriorityPass(cat, isOnDuty)) return true;
        return allowedMovements.contains(mvt);
    }

    // ── Getters ───────────────────────────────────────────────

    public int                  getIndex()           { return index;           }
    public Direction            getDirection()        { return direction;       }
    public double               getWidth()            { return width;           }
    public MarkingType          getLeftMarking()      { return leftMarking;     }
    public MarkingType          getRightMarking()     { return rightMarking;    }

    public Set<VehicleCategory> getAllowedVehicles()  {
        return Collections.unmodifiableSet(allowedVehicles);
    }

    /** Getter bị thiếu trong version trước — cần thiết để AI query hướng rẽ. */
    public Set<Movement>        getAllowedMovements() {
        return Collections.unmodifiableSet(allowedMovements);
    }

    // ── toString ──────────────────────────────────────────────

    @Override
    public String toString() {
        return String.format("Lane[%d] | %s | %.1fm | L:%s R:%s | %s | %s",
                index, direction, width,
                leftMarking, rightMarking,
                allowedVehicles, allowedMovements);
    }
}