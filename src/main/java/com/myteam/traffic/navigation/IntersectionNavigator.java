package com.myteam.traffic.navigation;

import com.myteam.traffic.model.geometry.GeometryUtils;
import com.myteam.traffic.model.geometry.Position;
import com.myteam.traffic.model.infrastructure.ConnectionPoint;
import com.myteam.traffic.model.infrastructure.Lane;
import com.myteam.traffic.model.infrastructure.RoadNetwork;
import com.myteam.traffic.model.infrastructure.RoadSegment;
import com.myteam.traffic.model.infrastructure.intersection.Intersection;
import com.myteam.traffic.vehicle.PlannedExit;
import com.myteam.traffic.vehicle.TravelMode;
import com.myteam.traffic.vehicle.Vehicle;
import com.myteam.traffic.vehicle.VehicleType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class IntersectionNavigator {

    private static final double INTERSECTION_SEARCH_RADIUS = 60.0;

    /**
     * Khoảng cách an toàn CƠ BẢN giữa hai quỹ đạo qua giao lộ.
     * Khoảng cách thực tế = MERGE_SAFE_DISTANCE + (chiều dài xe A + chiều dài xe B) / 2.
     * Cộng thêm kích thước xe để xe to (FireTruck) không bị "nuốt" vào xe nhỏ
     * khi hai quỹ đạo đi gần nhau.
     */
    private static final double MERGE_SAFE_DISTANCE = 30.0;

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

        // Lấy tọa độ tâm LÀN VÀO
        double inT = (inLane.getDirection() == Lane.Direction.FORWARD) ? 1.0 : 0.0;
        double[] inPose = inSeg.getPositionOnLane(inLane.getIndex(), inT);
        double startX = inPose[0];
        double startY = inPose[1];
        // Hướng THỰC TẾ xe đang di chuyển — xem effectiveHeadingDeg()
        double startHeading = effectiveHeadingDeg(inSeg, inLane, inT, inPose[2]);

        // Lấy tọa độ tâm LÀN RA
        double outT = (exitCP.getEnd() == ConnectionPoint.End.START) ? 0.0 : 1.0;
        double[] outPose = outSeg.getPositionOnLane(outLane.getIndex(), outT);
        double endX = outPose[0];
        double endY = outPose[1];
        double endHeading = effectiveHeadingDeg(outSeg, outLane, outT, outPose[2]);

        // Trả về quỹ đạo Bezier nối thẳng từ tâm làn này sang tâm làn kia
        return new IntersectionPath(intersection, entryCP, exitCP,
                startX, startY, startHeading,
                endX, endY, endHeading);
    }

    /**
     * Tính heading THỰC TẾ (degrees) mà xe đang hướng tới khi ở vị trí (idx, t) trên lane.
     *
     * {@code RoadSegment.getPositionOnLane()} trả về góc dựa trên hướng hình học
     * START → END của segment, KHÔNG xét {@code lane.getDirection()}.
     *
     * Với làn {@code FORWARD} (xe chạy START → END): heading trả về đã đúng,
     * không cần sửa.
     *
     * Với làn {@code BACKWARD} (xe chạy END → START): xe di chuyển NGƯỢC
     * 180° so với heading hình học của segment. Nếu không sửa, control point
     * đầu tiên của đường cong Bezier (IntersectionPath) sẽ trỏ NGƯỢC hướng
     * xe đang đi tới — khiến đường cong phải "gấp lại" tạo hình chữ Z hoặc
     * số 4 trước khi đến điểm cuối.
     *
     * @param seg       Đoạn đường chứa lane
     * @param lane      Làn đường (FORWARD hoặc BACKWARD)
     * @param t         Tham số vị trí trên lane (0..1), không dùng trực tiếp
     *                   nhưng giữ lại cho rõ ngữ cảnh gọi
     * @param rawHeadingDeg Giá trị heading thô trả về từ getPositionOnLane()[2]
     * @return Heading đã hiệu chỉnh theo hướng di chuyển thực tế của xe
     */
    private double effectiveHeadingDeg(RoadSegment seg, Lane lane, double t, double rawHeadingDeg) {
        if (lane.getDirection() == Lane.Direction.BACKWARD) {
            return GeometryUtils.normalizeAngle(rawHeadingDeg + 180.0);
        }
        return rawHeadingDeg;
    }

    public boolean canMerge(Vehicle entering, Intersection intersection, List<Vehicle> allVehicles) {
        // Tạm thời tạo đường cong cho xe sắp vào dựa trên PlannedExit hiện tại
        PlannedExit originalExit = entering.getPlannedExit();
        if (originalExit == PlannedExit.NONE) return true; // không có ý định rẽ → không vào giao lộ?

        // Lưu lại plannedExit tạm thời (xe chưa chính thức chuyển mode)
        IntersectionPath futurePath = buildTemporaryPath(entering, intersection);
        if (futurePath == null) return true;

        // Khoảng cách an toàn được "nở" theo kích thước của xe sắp vào.
        // Cộng thêm kích thước xe other (bên trong vòng lặp) để có
        // khoảng cách an toàn riêng cho từng cặp xe.
        double enteringHalfLen = vehicleLength(entering) / 2.0;

        for (Vehicle other : allVehicles) {
            if (other == entering) continue;
            if (other.getTravelMode() != TravelMode.ON_INTERSECTION_PATH) continue;
            if (other.getCurrentIntersection() != intersection) continue;

            IntersectionPath otherPath = other.getActivePath();
            if (otherPath == null) continue;

            // Lấy mẫu trên cả hai đường cong để tìm khoảng cách gần nhất
            double minDist = findMinDistanceBetweenPaths(futurePath, otherPath);

            // Khoảng cách an toàn = nền tảng + nửa chiều dài của CẢ HAI xe.
            // Xe to (FireTruck/Ambulance) cần khoảng cách lớn hơn để không
            // "nuốt chửng" xe nhỏ khi hai quỹ đạo đi gần nhau.
            double safeDist = MERGE_SAFE_DISTANCE + enteringHalfLen + vehicleLength(other) / 2.0;

            if (minDist < safeDist) {
                return false;
            }
        }
        return true;
    }

    /**
     * Ước lượng chiều dài xe (world units) theo loại xe.
     *
     * Dùng để mở rộng khoảng cách an toàn theo kích thước thực tế —
     * tránh trường hợp xe to (FireTruck) bị coi là "đủ xa" theo một
     * ngưỡng cố định trong khi thực tế thân xe đã chạm vào xe khác.
     */
    private double vehicleLength(Vehicle v) {
        return switch (v.getType()) {
            case BICYCLE   -> 1.8;
            case MOTORBIKE -> 2.2;
            case CAR       -> 4.5;
            case AMBULANCE -> 6.0;
            case FIRETRUCK -> 8.0;
        };
    }

    /**
    * Tạo một IntersectionPath tạm thời (không gắn vào xe) để kiểm tra va chạm.
    */
    private IntersectionPath buildTemporaryPath(Vehicle vehicle, Intersection intersection) {
    RoadSegment segment = vehicle.getCurrentSegment();
    Lane lane = vehicle.getCurrentLane();
    PlannedExit planned = vehicle.getPlannedExit();
    if (segment == null || lane == null || planned == PlannedExit.NONE) return null;

    ConnectionPoint entry = findEntryConnection(intersection, segment);
    if (entry == null) return null;

    List<ConnectionPoint> validExits = listValidExits(intersection, entry);
    if (validExits.isEmpty()) return null;

    ConnectionPoint exitCP = selectExit(intersection, entry, validExits, planned);
    if (exitCP == null) return null;

    RoadSegment outSeg = exitCP.getSegment();
    Lane outLane = pickDepartureLane(outSeg, exitCP.getEnd());

    // --- Tính điểm vào (tâm làn hiện tại tại đầu mút segment) ---
    double inT = (lane.getDirection() == Lane.Direction.FORWARD) ? 1.0 : 0.0;
    double[] inPose = segment.getPositionOnLane(lane.getIndex(), inT);
    double startX = inPose[0];
    double startY = inPose[1];
    double startHeadingDeg = effectiveHeadingDeg(segment, lane, inT, inPose[2]);

    // --- Tính điểm ra (tâm làn trên segment ra) ---
    double outT = (exitCP.getEnd() == ConnectionPoint.End.START) ? 0.0 : 1.0;
    double[] outPose = outSeg.getPositionOnLane(outLane.getIndex(), outT);
    double endX = outPose[0];
    double endY = outPose[1];
    double endHeadingDeg = effectiveHeadingDeg(outSeg, outLane, outT, outPose[2]);

    // Tạo IntersectionPath tạm thời (không gắn vào xe)
    return new IntersectionPath(intersection, entry, exitCP,
            startX, startY, startHeadingDeg,
            endX, endY, endHeadingDeg);
    }

/**
 * Tính khoảng cách gần nhất giữa hai đường cong Bezier bằng cách lấy mẫu.
 */
private double findMinDistanceBetweenPaths(IntersectionPath p1, IntersectionPath p2) {
    double minDist = Double.MAX_VALUE;
    // Lấy mẫu 20 điểm trên mỗi đường (đủ chính xác cho mô phỏng)
    for (int i = 0; i <= 20; i++) {
        double t1 = i / 20.0;
        double[] pos1 = p1.sampleAt(t1 * p1.getPathLength());
        Position pos1Pos = new Position(pos1[0], pos1[1]);

        for (int j = 0; j <= 20; j++) {
            double t2 = j / 20.0;
            double[] pos2 = p2.sampleAt(t2 * p2.getPathLength());
            double d = pos1Pos.distanceTo(new Position(pos2[0], pos2[1]));
            if (d < minDist) minDist = d;
            }
        }
        return minDist;
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