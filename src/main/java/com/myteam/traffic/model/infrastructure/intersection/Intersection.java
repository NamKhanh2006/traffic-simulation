package traffic.model.infrastructure.intersection;
import traffic.model.infrastructure.RoadSegment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Giao lộ
 * Muốn add thì kế thừa lớp này
 */
public abstract class Intersection {
    private double centerX;
    private double centerY;
    /**
     * list đường kết nối với giao lộ (ngã 3,ngã 4,ngã 5 ....)     */
    private final List<RoadSegment> connectedRoads = new ArrayList<>();

    /**
     * @param centerX tọa độ X chính giữa giao lộ
     * @param centerY tọa độ Y chính giữa giao lộ
     */

    public Intersection(double centerX, double centerY) {
        this.centerX = centerX;
        this.centerY = centerY;
    }


    public void connectRoad(RoadSegment road) {
        if (connectedRoads.size() >= getExpectedRoadCount()) {
            throw new IllegalStateException(
                    String.format("%s has reached its maximum capacity of %d roads.",
                            getIntersectionType(), getExpectedRoadCount())
            );
        }
        connectedRoads.add(road);
    }

    /**
     * Ngắt kết nối một đoạn đường khỏi giao lộ.
     * @param road Đoạn đường cần xóa.
     * @throws IllegalArgumentException Nếu không tìm thấy đoạn đường trong giao lộ.
     */
    public void disconnectRoad(RoadSegment road) {
        if (road == null) {
            throw new IllegalArgumentException("Road segment cannot be null.");
        }

        boolean removed = connectedRoads.remove(road);

        if (!removed) {
            throw new IllegalArgumentException("Road segment not found in this intersection.");
        }
    }

    public boolean replaceRoad(RoadSegment oldRoad,RoadSegment newRoad) {
        int index = connectedRoads.indexOf(oldRoad);
        if(index == -1) {return false;}
        connectedRoads.set(index, newRoad);
        return true;
    }

    public List<RoadSegment> getConnectedRoads() {
        return Collections.unmodifiableList(connectedRoads);
    }

    public int getRoadCount() {
        return connectedRoads.size();
    }

    public abstract int getExpectedRoadCount();
    public abstract String getIntersectionType();

    public double getCenterX() {
        return centerX;
    }

    public double getCenterY() {
        return centerY;
    }

    public void setCenterX(double centerX,double centerY) {
        this.centerX = centerX;
        this.centerY = centerY;
    }

    @Override
    public String toString() {
        return String.format("%s tại (%.0f, %.0f) — %d/%d đường",
                getIntersectionType(), centerX, centerY,
                connectedRoads.size(), getExpectedRoadCount());
    }
}

