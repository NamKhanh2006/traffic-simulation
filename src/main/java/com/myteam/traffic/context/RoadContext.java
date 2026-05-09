package com.myteam.traffic.context;

import java.util.List;
import com.myteam.traffic.sign.TrafficSign;
import com.myteam.traffic.marking.RoadMarking;
import com.myteam.traffic.light.TrafficLight;
// remember to import Lane and RoadSegment classes when start coding the methods

public class RoadContext {
    List<TrafficSign> signs;
    List<RoadMarking> markings;
    List<TrafficLight> lights;
    Lane currentLane;
    RoadSegment currentRoad;
}