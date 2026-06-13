package com.myteam.traffic.vehicle;

import com.myteam.traffic.model.infrastructure.Lane;

public enum VehicleType {
	BICYCLE,
	CAR,
	MOTORBIKE,
	AMBULANCE,
    FIRETRUCK;

	public Lane.VehicleCategory toVehicleCategory() {
        return switch (this) {
            case CAR       -> Lane.VehicleCategory.CAR;
            case MOTORBIKE -> Lane.VehicleCategory.MOTORBIKE;
            case BICYCLE   -> Lane.VehicleCategory.BICYCLE;
            case AMBULANCE, FIRETRUCK -> Lane.VehicleCategory.EMERGENCY;
            default        -> Lane.VehicleCategory.CAR;
        };
    }

    @Override
    public String toString(){
        return switch (this){
            case BICYCLE -> "Bicycle";
            case CAR -> "Car";
            case MOTORBIKE -> "Motorbike";
            case AMBULANCE -> "Ambulance";
            case FIRETRUCK -> "Firetruck";
            default -> "Car";
        };
    }
}
