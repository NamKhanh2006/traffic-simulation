package com.myteam.traffic.model.infrastructure.intersection;

// FIX #7: Bỏ import Collections thừa (không dùng ở đâu)
public class ThreeWayIntersection extends Intersection {

    public enum SubType { T_SHAPE, Y_SHAPE, UNKNOWN }
    private final SubType subType;

    public ThreeWayIntersection(double centerX, double centerY) {
        this(centerX, centerY, SubType.UNKNOWN);
    }

    public ThreeWayIntersection(double centerX, double centerY, SubType subType) {
        super(centerX, centerY);
        this.subType = (subType == null) ? SubType.UNKNOWN : subType;
    }

    @Override
    public int getExpectedRoadCount() {
        return 3;
    }

    @Override
    public String getIntersectionType() {
        return switch (subType) {
            case T_SHAPE -> "Ngã ba hình chữ T";
            case Y_SHAPE -> "Ngã ba hình chữ Y";
            default      -> "Ngã ba (Chưa xác định)";
        };
    }

    public boolean isSharpTurn() {
        return subType == SubType.T_SHAPE;
    }

    public SubType getSubType() {
        return subType;
    }

    @Override
    public String toString() {
        return String.format("ThreeWayIntersection[Type=%s, Pos=(%.1f, %.1f)]",
                subType, centerX, centerY);
    }
}