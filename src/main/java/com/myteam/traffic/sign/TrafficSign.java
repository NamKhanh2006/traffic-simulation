package com.myteam.traffic.sign;

import traffic.model.infrastructure.RoadSegment;

public class TrafficSign {
    private int xPos;
    private int yPos;
    private SignType type;
    private String code;
    private RoadSegment roadSeg;

    public TrafficSign(int xPos, int yPos, SignType type, String code, RoadSegment roadSeg) {
        this.xPos = xPos;
        this.yPos = yPos;
        this.type = type;
        this.code = code;
        this.roadSeg = roadSeg;
    }

    // Getters and setters
    public int getXPos() {
        return xPos;
    }
    public void setXPos(int xPos) {
        this.xPos = xPos;
    }
    public int getYPos() {
        return yPos;
    }
    public void setYPos(int yPos) {
        this.yPos = yPos;
    }
    public SignType getType() {
        return type;
    }
    public void setType(SignType type) {
        this.type = type;
    }

    // boolean isApplicable(Vehicle v, Action a, RoadContext c) {
    //     // Logic to determine if this sign applies to the given vehicle, action, and road context
    // }
}
