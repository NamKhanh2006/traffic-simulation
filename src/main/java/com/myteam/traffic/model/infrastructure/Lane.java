package com.myteam.traffic.model.infrastructure;

import java.util.EnumSet;
import java.util.Set;

/**
 * Đại diện cho một làn đường bên trong một RoadSegment.
 * index từ 0
 */
public class Lane {
    /**
     * Phân lọại vạch
     */
    public enum MarkingType{
        NONE, // không có vạch
        DASHED, // vạch liền đứt
        SOLID, // vạch liền
        DOUBLE_SOLID, // vạch liền kép
        YELLOW_SOLID // vạch vàng
    }

    private MarkingType leftMarking;
    private MarkingType rightMarking;
    private boolean isHighOccupancy;

    public enum VehicleCategory {
        CAR, MOTORBIKE, BUS, BICYCLE, EMERGENCY, TRUCK
    }

    /**
     * hướng của làn đường
     */
    public enum Direction {
        FORWARD,   // cùng chiều với RoadSegment (từ start → end)
        BACKWARD   // ngược chiều (từ end → start)
    }

    /**
     * Hướng đi
     */
    public enum Movement{
        RIGHT, // rẽ phải
        LEFT, // rẽ trái
        STRAIGHT, // đi thẳng
        U_TURN // quay đầu
    }



    private int index;          // Vị trí làn: 0, 1, 2, ...
    private Direction direction;
    private double width;       // Chiều rộng làn (pixel)

    private Set<VehicleCategory> allowedVehicles; // danh sách các loại phương tiên được đi
    private Set<Movement>  allowedMovements; // hướng đi cho phép của làn (vd được phép rẽ phải hoặc đi thẳng)

    public Lane(int index, Direction direction, double width,Set<VehicleCategory> allowedVehicles, Set<Movement> allowedMovements,MarkingType leftMarking, MarkingType rightMarking) {
        this.index = index;
        this.direction = direction;
        this.width = width;
        this.allowedVehicles = EnumSet.copyOf(allowedVehicles);
        this.allowedMovements = EnumSet.copyOf(allowedMovements);
        this.leftMarking = leftMarking;
        this.rightMarking = rightMarking;
    }


    // Getter

    public int getIndex() {
        return index;
    }

    public Direction getDirection() {
        return direction;
    }

    public double getWidth() {
        return width;
    }

    public MarkingType getLeftMarking() {return leftMarking;}

    public MarkingType getRightMarking() {return rightMarking;}

    // Các tiện ích khác

    public boolean canCrossLeft() {
        return leftMarking == MarkingType.DASHED;
    }

    public boolean canCrossRight() {
        return rightMarking == MarkingType.DASHED;
    }

    public boolean isAllowedVehicle(VehicleCategory vehicleCategory) {
        if(vehicleCategory == VehicleCategory.EMERGENCY) return true;
        return allowedVehicles.contains(vehicleCategory);
    }

    public boolean allows(Movement movement) {
        return allowedMovements.contains(movement);
    }

    @Override
    public String toString() {
        return "Lane{index=" + index + ", dir=" + direction + ", width=" + width + "}";
    }
}
