package com.myteam.traffic.marking;

import com.myteam.traffic.geometry.Geometry;
// remember to import RoadSegment and Lane classes when start coding the methods

public class RoadMarking {
    private MarkingType type;
    private Geometry shape;
    private RoadSegment roadSeg;
    private List<Lane> relatedLanes;

    public RoadMarking(MarkingType type, Geometry shape, RoadSegment roadSeg, List<Lane> lanes) {
        this.type = type;
        this.shape = shape;
        this.roadSeg = roadSeg;
        this.lanes = lanes;
    }

    public MarkingType getType() {
        return type;
    }
    public Geometry getShape() {
        return shape;
    }
    public RoadSegment getRoadSeg() {
        return roadSeg;
    }
    public List<Lane> getLanes() {
        return lanes;
    }

    public void setType(MarkingType type) {
        this.type = type;
    }
    public void setShape(Geometry shape) {
        this.shape = shape;
    }
    public void setRoadSeg(RoadSegment roadSeg) {
        this.roadSeg = roadSeg;
    }
    public void setLanes(List<Lane> lanes) {
        this.lanes = lanes;
    }

    public boolean isVehicleCrossing(Vehicle v) {
        // implement logic to check if the vehicle is crossing the marking
        // this may involve checking the vehicle's position against the shape of the marking
        return false; // placeholder return value
    }
}
