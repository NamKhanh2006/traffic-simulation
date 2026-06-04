package com.myteam.traffic.rule;
import java.util.HashSet;
import com.myteam.traffic.behavior.*;
import com.myteam.traffic.behavior.common.*;
import com.myteam.traffic.vehicle.*;
import com.myteam.traffic.vehicle.emergency.*;
import com.myteam.traffic.context.*;

public class ActionRule implements TrafficRule {
    private HashSet<Action> allowed;     
    private HashSet<Action> banned;      
    private HashSet<VehicleType> affectedVehicles; // Vehicles affected by the rule - Các loại xe bị luật này áp dụng
    // null = ALL VEHICLES AFFECTED

    // Constructor to generate different rules for different lanes
    // Constructor để tạo các luật khác nhau cho các làn khác nhau
    public ActionRule(HashSet<Action> allowed, HashSet<Action> banned, HashSet<VehicleType> vehicles) {
        this.allowed = allowed;
        this.banned = banned;
        this.affectedVehicles = vehicles;
    }

    @Override
    public boolean isAllowed(Vehicle v, Action a, RoadContext c) {
    	// If this rule isn't applied to the current vehicle, skip
        // Nếu luật này không áp dụng cho loại xe hiện tại, mặc định cho qua
        if (affectedVehicles != null && !affectedVehicles.contains(v.getType())) {
            return true;
        }
		
		// Check the list of banned vehicles first
        // Kiểm tra danh sách cấm trước (Banned)
        if (banned != null && banned.contains(a)) {
            return false;
        }
		
		// Check the list of allowed vehicles
        // Kiểm tra danh sách cho phép (Allowed)
        if (allowed != null && !allowed.contains(a)) {
            return false;
        }

        return true;
    }

    @Override
    public int getPriority() {
    	return 20;
    }
    
    @Override
    public boolean appliesTo(Vehicle v) {
        if (affectedVehicles == null) {
            return true;
        }
        return affectedVehicles.contains(v.getType());
    }
}
