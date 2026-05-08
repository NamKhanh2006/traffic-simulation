package com.myteam.traffic.model.infrastructure;

import java.util.*;
import com.myteam.traffic.model.policy.*;

public class Lane {
    public enum MarkingType {
        NONE, DASHED, SOLID, DOUBLE_SOLID, YELLOW_SOLID,
        LEFT_DASHED_RIGHT_SOLID, LEFT_SOLID_RIGHT_DASHED
    }
    public enum VehicleCategory { CAR, MOTORBIKE, BUS, BICYCLE, EMERGENCY, TRUCK }
    public enum Direction { FORWARD, BACKWARD }
    public enum Movement { STRAIGHT, LEFT, RIGHT, U_TURN }

    private final int index;
    private Direction direction;
    private double width;
    private SpeedPolicy customSpeedPolicy;

    private final Set<VehicleCategory> allowedVehicles;
    private final Set<Movement> allowedMovements;
    private MarkingType leftMarking;
    private MarkingType rightMarking;

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

        this.allowedVehicles = (vehicles == null || vehicles.isEmpty())
                ? EnumSet.noneOf(VehicleCategory.class) : EnumSet.copyOf(vehicles);
        this.allowedMovements = (movements == null || movements.isEmpty())
                ? EnumSet.noneOf(Movement.class) : EnumSet.copyOf(movements);
    }

    private boolean isPriorityPass(VehicleCategory cat, boolean isOnDuty) {
        return cat == VehicleCategory.EMERGENCY && isOnDuty;
    }

    /**
     * FIX #5: Logic vạch kẻ đã sửa cho đúng ngữ nghĩa.
     *
     * MarkingType mô tả vạch kẻ nhìn từ trong làn ra:
     *   LEFT_DASHED_RIGHT_SOLID  → bên trái làn này là nét đứt → có thể sang trái
     *   LEFT_SOLID_RIGHT_DASHED  → bên phải làn này là nét đứt → có thể sang phải
     */
    public boolean canCrossLeft(VehicleCategory cat, boolean isOnDuty) {
        if (isPriorityPass(cat, isOnDuty)) return true;
        return switch (leftMarking) {
            case DASHED -> true;
            // Vạch bên TRÁI là nét đứt → cho phép cắt sang trái
            case LEFT_DASHED_RIGHT_SOLID -> true;
            default -> false;
        };
    }

    public boolean canCrossRight(VehicleCategory cat, boolean isOnDuty) {
        if (isPriorityPass(cat, isOnDuty)) return true;
        return switch (rightMarking) {
            case DASHED -> true;
            // Vạch bên PHẢI là nét đứt → cho phép cắt sang phải
            case LEFT_SOLID_RIGHT_DASHED -> true;
            default -> false;
        };
    }

    public boolean allowsVehicle(VehicleCategory cat) {
        return allowedVehicles.contains(cat);
    }

    public boolean allowsMovement(Movement movement) {
        return allowedMovements.contains(movement);
    }

    public int getIndex() { return index; }
    public Direction getDirection() { return direction; }
    public double getWidth() { return width; }

    public Set<VehicleCategory> getAllowedVehicles() {
        return Collections.unmodifiableSet(allowedVehicles);
    }

    public Set<Movement> getAllowedMovements() {
        return Collections.unmodifiableSet(allowedMovements);
    }

    public SpeedPolicy getCustomSpeedPolicy() { return customSpeedPolicy; }
    public void setCustomSpeedPolicy(SpeedPolicy policy) { this.customSpeedPolicy = policy; }

    @Override
    public String toString() {
        String speedStr = (customSpeedPolicy != null) ? customSpeedPolicy.getLimit() + "km/h" : "Default";
        return String.format(
                "Lane[%d] | %s | Width: %.1f | Speed: %s | L-Marking: %s | R-Marking: %s",
                index, direction, width, speedStr, leftMarking, rightMarking
        );
    }
}