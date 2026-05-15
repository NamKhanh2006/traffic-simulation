package com.myteam.traffic.context;

import com.myteam.traffic.model.infrastructure.*;
import com.myteam.traffic.rule.*;
import com.myteam.traffic.sign.*;
import com.myteam.traffic.marking.RoadMarking;

import java.util.*;

public class RoadContext {

    private boolean redLight;
    private List<RoadMarking> markings = new ArrayList<>();
    private List<TrafficSign> signs = new ArrayList<>();
    private Lane currentLane;  // The Lane that the vehicle is currently on (each vehicle builds its own context)

    public boolean isRedLight() {
        return redLight;
    }

    public void setRedLight(boolean redLight) {
        this.redLight = redLight;
    }

    public List<RoadMarking> getMarkings() {
        return markings;
    }

    public void addMarking(RoadMarking m) {
        markings.add(m);
    }
    
    public List<TrafficRule> getLocalRules() {
        List<TrafficRule> localRules = new ArrayList<>();
        
        // Lấy luật từ biển báo trong context
        for (TrafficSign sign : this.signs) {
            if (sign.getRule() != null)
            	localRules.add(sign.getRule());
            // TODO: Write getRule() method to get the rule that a sign implements
        }
        
        // Lấy luật từ làn đường hiện tại
        if (this.currentLane != null) {
            localRules.addAll(this.currentLane.getRules());
            // TODO: Write the getRules() method for the Lane class to get rules applied on a lane
        }
        
        return localRules;
    }
    
}