package com.myteam.traffic.vehicle;

import com.myteam.traffic.model.infrastructure.Lane;

public enum VehicleType {
	BICYCLE,
	CAR,
	MOTORBIKE,
	EMERGENCY;

	public Lane.VehicleCategory toVehicleCategory() {
        return switch (this) {
            case CAR       -> Lane.VehicleCategory.CAR;
            case MOTORBIKE -> Lane.VehicleCategory.MOTORBIKE;
            case BICYCLE   -> Lane.VehicleCategory.BICYCLE;
            case EMERGENCY -> Lane.VehicleCategory.EMERGENCY;
            default        -> Lane.VehicleCategory.CAR;
        };
    }
}
