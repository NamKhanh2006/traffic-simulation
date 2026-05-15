package traffic.model.infrastructure.intersection;

public class ThreeWayIntersection extends Intersection {
    public ThreeWayIntersection(double centerX,double centerY){
        super(centerX,centerY);
    }

    @Override
    public int getExpectedRoadCount() {
        return 3;
    }

    @Override
    public String getIntersectionType() {
        return "Three-way intersection";
    }
}
