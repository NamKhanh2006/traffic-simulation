package com.myteam.traffic.rule;
import java.util.HashSet;

/*
public class ActionRule implements TrafficRule {
    private HashSet<Action> allowed;     // nếu null → không giới hạn
    private HashSet<Action> banned;      // nếu null → không cấm
    private HashSet<VehicleType> vehicles; // áp dụng cho loại xe nào (null = tất cả)

    public boolean isAllowed(Vehicle v, Action a, RoadContext c) {
        if (vehicles != null && !vehicles.contains(v.getType()))
        	return true;

        if (allowed != null && !allowed.contains(a))
        	return false;
        if (banned != null && banned.contains(a))
        	return false;
        	
        Lane currentLane = v.getLane();
        HashSet<allowedOnCurrentLane> = currentLane.getAllowedActions();
        
        if !allowedOnCurrentLane.contains(a)
        	return false;

        return true;
    }
}
*/
// The commented code above will be abandoned.


public class ActionRule implements TrafficRule {
    private HashSet<Action> allowed;     
    private HashSet<Action> banned;      
    private HashSet<VehicleType> affectedVehicles; // Vehicles affected by the rule - Các loại xe bị luật này áp dụng

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
    public int getPriority() { return 20; }
}
