package traffic.model.infrastructure;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;


/**
 * Đoạn đường bắt đầu từ A(startx,starty) đến B(endx,endy) với x , y là tọa độ điểm ở tim đường
 * Gốc tọa độ ở trên bên trái ,  x tăng theo chiều từ trái sang phải , y tăng theo chiều từ trên xuông dưới
 * Danh sách lane
 * Tất cả các đâị lương đều tính toán theo pixels góc tính theo radian
 */

public class RoadSegment {
    private double startX;
    private double startY;
    private double endX;
    private double endY;
    private double speedLimit;
    private List<Lane> lanes;

    /**
     * @param endX tọa độ X điểm cuối của đường
     * @param endY  tọa độ Y điểm cối của đường
     * @param startX tọa độ X đểm đầu của đường
     * @param startY tọa độ Y điểm đầu đường
     * @param speedLimit tốc độ tối đa mặc đinh của đường (vd đường trong khu dân cư là 50 km/h)
     */

    public RoadSegment(double startX, double startY, double endX, double endY,double speedLimit) {
        if(speedLimit<=0) throw new IllegalArgumentException("speedLimit must be greater than 0");
        this.startX = startX;
        this.startY = startY;
        this.endX = endX;
        this.endY = endY;
        this.speedLimit = speedLimit;
        this.lanes = new ArrayList<>();
    }

    public void addLane(Lane lane) {
        this.lanes.add(lane);
    }

    public void removeLane(int laneIndex) {
        lanes.removeIf(lane->lane.getIndex() == laneIndex);
    }


    public List<Lane> getLanes() {
        return Collections.unmodifiableList(this.lanes); // hàm này chỉ cho xem không cho sửa hay thêm làn đường khác
    }

    public int getLaneCount() {
        return lanes.size();
    }

    /** Chiều dài đường */
    public double getLength(){
        double dx = endX - startX;
        double dy = startY - endY;
        return Math.sqrt(dx * dx + dy * dy);
    }

    public double getTotalWidth() {
        double total = 0;
        for (Lane l : lanes) total += l.getWidth();
        return total;
    }

    /** Góc của hướng di chuyển theo trục X (radian) */
    public double getAngle(){
        double dx = endX - startX;
        double dy = startY - endY;
        return Math.atan2(dy, dx);
    }

    int laneIndex;
    int t;

    /**
     * @param t tỷ lệ (0.0 -> 1.0)
     * @return [x, y, angle] - angle để bạn mình xoay cái xe cho đúng hướng
     */
    public double[] getPositionOnLane(int laneIndex, double t) {
        if (laneIndex < 0 || laneIndex >= lanes.size()) {
            throw new IndexOutOfBoundsException("Invalid lane index");
        }

        double angle = getAngle();
        double perpAngle = angle + Math.PI / 2;

        // Tính offset từ tim đường (Centerline) đến tâm của làn cụ thể
        double totalWidth = getTotalWidth();
        double offset = -totalWidth / 2.0;

        for (int i = 0; i < laneIndex; i++) {
            offset += lanes.get(i).getWidth();
        }
        offset += lanes.get(laneIndex).getWidth() / 2.0;

        // Vị trí trên tim đường (Linear Interpolation)
        double cx = startX + t * (endX - startX);
        double cy = startY + t * (endY - startY);

        // Vị trí thực tế trên làn (Dịch chuyển theo hướng vuông góc)
        double x = cx + offset * Math.cos(perpAngle);
        double y = cy + offset * Math.sin(perpAngle);

        return new double[]{x, y, angle};
    }

    public double getStartX() { return startX; }
    public double getStartY() { return startY; }
    public double getEndX()   { return endX;   }
    public double getEndY()   { return endY;   }

    public void setStart(double x, double y){
        this.startX = x;
        this.startY = y;
    }

    public void setEnd(double x, double y){
        this.endX = x;
        this.endY = y;
    }

    public void setSpeedLimit(double speedLimit) {
        if(speedLimit<=0) throw new IllegalArgumentException("speedLimit must be greater than 0");
        this.speedLimit = speedLimit;
    }

    @Override
    public String toString() {
        return String.format("RoadSegment[(%,.0f,%,.0f)→(%,.0f,%,.0f)  %d lanes]",
                startX, startY, endX, endY,  lanes.size());
    }
}
