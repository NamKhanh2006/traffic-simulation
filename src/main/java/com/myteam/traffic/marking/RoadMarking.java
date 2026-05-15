package com.myteam.traffic.marking;

import com.myteam.traffic.model.geometry.Geometry;
import com.myteam.traffic.model.infrastructure.RoadSegment;
import com.myteam.traffic.model.infrastructure.Lane;

import java.util.List;

public class RoadMarking {

    private MarkingType type;
    private Geometry geometry;
    private RoadSegment road;
    private List<Lane> relatedLanes; // optional
    
    private boolean isCrossingAllowed;

    public RoadMarking(MarkingType type, Geometry geometry, RoadSegment road, boolean isCrossingAllowed) {
        this.type = type;
        this.geometry = geometry;
        this.road = road;
        this.isCrossingAllowed = isCrossingAllowed;
    }

    /*
    public boolean isVehicleCrossing(Vehicle v) {
        return geometry.intersects(v);
    }
    */

    public MarkingType getType() {
        return type;
    }
    
    public boolean getIsCrossingAllowed() {
    	return isCrossingAllowed;
    }
}

// The commented code BELOW will be abandoned.
/*
package com.myteam.traffic.marking;
import com.myteam.traffic.common.*;
import com.myteam.traffic.model.infrastructure.*;

public class RoadMarking {
	private MarkingType type;
	private float startX;
	private float startY;
	private float endX;
	private float endY;
	
	private Lane[] lanes = new Lane[2]; // A road marking is always between two lanes
	
}
*/
