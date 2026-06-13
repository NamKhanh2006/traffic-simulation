package com.myteam.traffic.navigation;

import com.myteam.traffic.model.infrastructure.ConnectionPoint;
import com.myteam.traffic.model.infrastructure.Lane;
import com.myteam.traffic.model.infrastructure.RoadNetwork;
import com.myteam.traffic.model.infrastructure.RoadSegment;
import com.myteam.traffic.model.infrastructure.intersection.Intersection;
import com.myteam.traffic.vehicle.PlannedExit;
import com.myteam.traffic.vehicle.TravelMode;
import com.myteam.traffic.vehicle.Vehicle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class IntersectionNavigator {

    private static final double INTERSECTION_SEARCH_RADIUS = 60.0;
    private static final double MERGE_SAFE_DISTANCE = 45.0;

    private final RoadNetwork network;

    public IntersectionNavigator(RoadNetwork network) {
        this.network = network;
    }

    public Intersection peekUpcomingIntersection(Vehicle vehicle) {
        RoadSegment segment = vehicle.getCurrentSegment();
        Lane lane = vehicle.getCurrentLane();
        if (segment == null || lane == null) return null;
        return findIntersectionAtSegmentEnd(segment, lane);
    }

    public IntersectionPath buildPath(Vehicle vehicle) {
        RoadSegment segment = vehicle.getCurrentSegment();
        Lane lane = vehicle.getCurrentLane();
        PlannedExit planned = vehicle.getPlannedExit();

        if (segment == null || lane == null || planned == PlannedExit.NONE) return null;

        Intersection intersection = findIntersectionAtSegmentEnd(segment, lane);
        if (intersection == null) return null;

        ConnectionPoint entry = findEntryConnection(intersection, segment);
        if (entry == null) return null;

        List<ConnectionPoint> validExits = listValidExits(intersection, entry);
        if (validExits.isEmpty()) return null;

        ConnectionPoint exitCP = selectExit(intersection, entry, validExits, planned);
        if (exitCP == null) return null;

        RoadSegment outSeg = exitCP.getSegment();
        Lane outLane = pickDepartureLane(outSeg, exitCP.getEnd());

        return buildPath(intersection, segment, lane, entry, outSeg, outLane, exitCP);
    }

    public IntersectionPath buildPath(Intersection intersection,
                                      RoadSegment inSeg, Lane inLane, ConnectionPoint entryCP,
                                      RoadSegment outSeg, Lane outLane, ConnectionPoint exitCP) {

        // Lấy tọa độ và góc chính xác của tâm LÀN VÀO
        double inT = (inLane.getDirection() == Lane.Direction.FORWARD) ? 1.0 : 0.0;
        double[] inPose = inSeg.getPositionOnLane(inLane.getIndex(), inT);
        double startX = inPose[0];
        double startY = inPose[1];
        double startHeading = inPose[2];

        // Lấy tọa độ và góc chính xác của tâm LÀN RA
        double outT = (exitCP.getEnd() == ConnectionPoint.End.START) ? 0.0 : 1.0;
        double[] outPose = outSeg.getPositionOnLane(outLane.getIndex(), outT);
        double endX = outPose[0];
        double endY = outPose[1];
        double endHeading = outPose[2];

        // Trả về quỹ đạo Bezier nối thẳng từ tâm làn này sang tâm làn kia
        return new IntersectionPath(intersection, entryCP, exitCP,
                startX, startY, startHeading,
                endX, endY, endHeading);
    }

    public boolean canMerge(Vehicle entering, Intersection intersection, List<Vehicle> allVehicles) {
        ConnectionPoint entry = findEntryConnection(intersection, entering.getCurrentSegment());
        if (entry == null) return true;

        double cx = intersection.getCenterX();
        double cy = intersection.getCenterY();
        double r = intersection.getRenderData().radius;
        double entryAngle = Math.atan2(entry.getY() - cy, entry.getX() - cx);

        double mergeX = cx + r * Math.cos(entryAngle);
        double mergeY = cy + r * Math.sin(entryAngle);

        for (Vehicle other : allVehicles) {
            if (other == entering) continue;
            if (other.getTravelMode() != TravelMode.ON_INTERSECTION_PATH) continue;
            if (other.getCurrentIntersection() != intersection) continue;

            double dx = other.getPosition().getX() - mergeX;
            double dy = other.getPosition().getY() - mergeY;

            if (Math.hypot(dx, dy) < MERGE_SAFE_DISTANCE) {
                return false;
            }
        }
        return true;
    }

    public void applyExit(Vehicle vehicle) {
        IntersectionPath path = vehicle.getActivePath();
        if (path == null) return;

        ConnectionPoint exit = path.getExit();
        RoadSegment segment = exit.getSegment();
        Lane lane = pickDepartureLane(segment, exit.getEnd());

        double t = (exit.getEnd() == ConnectionPoint.End.START) ? 0.0 : 1.0;
        vehicle.exitToSegment(segment, lane, t);
    }

    public List<ConnectionPoint> listValidExits(Intersection intersection, ConnectionPoint entry) {
        List<ConnectionPoint> valid = new ArrayList<>();
        for (ConnectionPoint cp : intersection.getConnections()) {
            if (cp != entry) valid.add(cp);
        }
        return valid;
    }

    private ConnectionPoint selectExit(Intersection intersection,
                                       ConnectionPoint entry,
                                       List<ConnectionPoint> validExits,
                                       PlannedExit planned) {
        if (planned == PlannedExit.RANDOM) {
            return validExits.get(ThreadLocalRandom.current().nextInt(validExits.size()));
        }

        double cx = intersection.getCenterX();
        double cy = intersection.getCenterY();

        List<ConnectionPoint> sorted = new ArrayList<>(intersection.getConnections());
        sorted.sort(Comparator.comparingDouble(cp ->
                Math.atan2(cp.getY() - cy, cp.getX() - cx)));

        int entryIdx = sorted.indexOf(entry);
        if (entryIdx < 0) return null;

        int n = sorted.size();
        ConnectionPoint target = switch (planned) {
            case LEFT -> sorted.get((entryIdx + 1) % n);
            case RIGHT -> sorted.get((entryIdx - 1 + n) % n);
            case STRAIGHT -> pickOppositeArm(validExits, entry, cx, cy);
            case U_TURN -> pickOppositeArm(validExits, entry, cx, cy);
            default -> null;
        };

        if (target == null || target == entry || !validExits.contains(target)) {
            return pickOppositeArm(validExits, entry, cx, cy);
        }
        return target;
    }

    private ConnectionPoint pickOppositeArm(List<ConnectionPoint> arms, ConnectionPoint entry, double cx, double cy) {
        double entryAngle = Math.atan2(entry.getY() - cy, entry.getX() - cx);
        double targetAngle = entryAngle + Math.PI;

        ConnectionPoint best = null;
        double bestDelta = Double.MAX_VALUE;

        for (ConnectionPoint cp : arms) {
            if (cp == entry) continue;
            double angle = Math.atan2(cp.getY() - cy, cp.getX() - cx);

            double delta = angle - targetAngle;
            while (delta <= -Math.PI) delta += 2 * Math.PI;
            while (delta > Math.PI) delta -= 2 * Math.PI;
            delta = Math.abs(delta);

            if (delta < bestDelta) {
                bestDelta = delta;
                best = cp;
            }
        }
        return best;
    }

    private Intersection findIntersectionAtSegmentEnd(RoadSegment segment, Lane lane) {
        double x, y;
        if (lane.getDirection() == Lane.Direction.FORWARD) {
            x = segment.getEndX(); y = segment.getEndY();
        } else {
            x = segment.getStartX(); y = segment.getStartY();
        }
        return network.findNearestIntersection(x, y, INTERSECTION_SEARCH_RADIUS);
    }

    ConnectionPoint findEntryConnection(Intersection intersection, RoadSegment segment) {
        for (ConnectionPoint cp : intersection.getConnections()) {
            if (cp.getSegment() == segment) return cp;
        }
        return null;
    }

    private Lane pickDepartureLane(RoadSegment segment, ConnectionPoint.End end) {
        List<Lane> lanes = segment.getLanes();
        Lane.Direction desired = (end == ConnectionPoint.End.START)
                ? Lane.Direction.FORWARD
                : Lane.Direction.BACKWARD;

        for (Lane lane : lanes) {
            if (lane.getDirection() == desired) return lane;
        }
        return lanes.get(0);
    }
}