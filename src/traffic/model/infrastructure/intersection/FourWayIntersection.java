package traffic.model.infrastructure.intersection;

public class FourWayIntersection extends Intersection {

    public FourWayIntersection(double centerX, double centerY) {
        super(centerX, centerY);
    }

    @Override public int    getExpectedRoadCount() { return 4;        }
    @Override public String getIntersectionType()  { return "Four-way intersection";}
}
