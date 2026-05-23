package com.myteam.traffic.model.infrastructure;
import java.util.*;

/**
 * Làn đường vật lý — fully immutable.
 * Mọi thay đổi đều tạo object mới qua wither methods.
 */
public final class Lane {

    // ── Enums ─────────────────────────────────────────────────

    public enum MarkingType {
        NONE,
        DASHED,
        SOLID,
        DOUBLE_SOLID,
        YELLOW_SOLID,
        YELLOW_DASHED,
        YELLOW_DOUBLE_SOLID,
        LEFT_DASHED_RIGHT_SOLID,
        LEFT_SOLID_RIGHT_DASHED,
        YELLOW_LEFT_DASHED_RIGHT_SOLID,
        YELLOW_LEFT_SOLID_RIGHT_DASHED
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

    public Lane withAddedVehicle(VehicleCategory cat) {
        Set<VehicleCategory> updated = EnumSet.copyOf(allowedVehicles);
        updated.add(cat);
        return new Lane(index, direction, width,
                updated, allowedMovements, leftMarking, rightMarking);
    }

    public Lane withRemovedVehicle(VehicleCategory cat) {
        Set<VehicleCategory> updated = allowedVehicles.isEmpty()
                ? EnumSet.noneOf(VehicleCategory.class) : EnumSet.copyOf(allowedVehicles);
        updated.remove(cat);
        return new Lane(index, direction, width,
                updated, allowedMovements, leftMarking, rightMarking);
    }

    public Lane withAddedMovement(Movement mvt) {
        Set<Movement> updated = EnumSet.copyOf(allowedMovements);
        updated.add(mvt);
        return new Lane(index, direction, width,
                allowedVehicles, updated, leftMarking, rightMarking);
    }

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
     * YELLOW_LEFT_SOLID_RIGHT_DASHED: nét đứt về phía làn hiện tại → được qua (giống LEFT_SOLID_RIGHT_DASHED).
     */
    public boolean canCrossLeft(VehicleCategory cat, boolean isOnDuty) {
        if (isPriorityPass(cat, isOnDuty)) return true;
        return switch (leftMarking) {
            case DASHED, YELLOW_DASHED, LEFT_SOLID_RIGHT_DASHED, YELLOW_LEFT_SOLID_RIGHT_DASHED -> true;
            default -> false;
        };
    }

    /**
     * Kiểm tra xe có thể đổi sang làn bên PHẢI không.
     * YELLOW_LEFT_DASHED_RIGHT_SOLID: nét đứt về phía làn hiện tại → được qua (giống LEFT_DASHED_RIGHT_SOLID).
     */
    public boolean canCrossRight(VehicleCategory cat, boolean isOnDuty) {
        if (isPriorityPass(cat, isOnDuty)) return true;
        return switch (rightMarking) {
            case DASHED, YELLOW_DASHED, LEFT_DASHED_RIGHT_SOLID, YELLOW_LEFT_DASHED_RIGHT_SOLID -> true;
            default -> false;
        };
    }

    public boolean allowsVehicle(VehicleCategory cat, boolean isOnDuty) {
        if (isPriorityPass(cat, isOnDuty)) return true;
        return allowedVehicles.contains(cat);
    }

    public boolean allowsMovement(Movement mvt, VehicleCategory cat, boolean isOnDuty) {
        if (isPriorityPass(cat, isOnDuty)) return true;
        return allowedMovements.contains(mvt);
    }
    

	// ── KHÁNH'S BACKWARD COMPATIBILITY BRIDGE ─────────────────
	// Thêm các hàm này để các class Rule cũ của Khánh không bị lỗi compile
	public boolean isAllowedVehicle(VehicleCategory vehicleCategory) {
		// Mặc định xe ưu tiên luôn được đi, các xe khác xét theo danh sách cho phép
		if (vehicleCategory == VehicleCategory.EMERGENCY) return true;
		return allowedVehicles.contains(vehicleCategory);
	}
	
	public boolean allows(Movement movement) {
		return allowedMovements.contains(movement);
	}
	
	public boolean canCrossLeft() {
		return leftMarking == MarkingType.DASHED || leftMarking == MarkingType.YELLOW_DASHED;
	}
	public boolean canCrossRight() {
		return rightMarking == MarkingType.DASHED || rightMarking == MarkingType.YELLOW_DASHED;
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

