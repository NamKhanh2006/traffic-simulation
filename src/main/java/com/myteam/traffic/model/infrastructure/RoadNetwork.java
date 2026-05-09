package com.myteam.traffic.model.infrastructure;

import com.myteam.traffic.model.infrastructure.intersection.Intersection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Lớp RoadNetwork quản lý toàn bộ hệ thống thực thể hạ tầng giao thông.
 * 
 * <p>Lớp này đóng vai trò là một Container trung tâm, giữ danh sách các đoạn đường 
 * (RoadSegment) và các nút giao (Intersection). Nó đảm bảo rằng các mối liên kết 
 * giữa đường và nút giao luôn được đồng bộ khi có sự thay đổi (thêm, xóa, sửa).</p>
 */
public class RoadNetwork {

    /** Danh sách tất cả các đoạn đường trong mạng lưới. */
    private final List<RoadSegment>  segments      = new ArrayList<>();
    
    /** Danh sách tất cả các nút giao trong mạng lưới. */
    private final List<Intersection> intersections = new ArrayList<>();

    // ── THÊM ─────────────────────────────────────────────────

    /**
     * Thêm một đoạn đường mới vào hệ thống.
     * 
     * @param segment Đoạn đường cần thêm.
     * @throws IllegalArgumentException nếu đối tượng truyền vào là null.
     */
    public void addSegment(RoadSegment segment) {
        if (segment == null) throw new IllegalArgumentException("Segment không được null");
        if (!segments.contains(segment)) segments.add(segment);
    }

    /**
     * Thêm một nút giao mới vào hệ thống.
     * 
     * @param intersection Nút giao cần thêm.
     * @throws IllegalArgumentException nếu đối tượng truyền vào là null.
     */
    public void addIntersection(Intersection intersection) {
        if (intersection == null) throw new IllegalArgumentException("Intersection không được null");
        if (!intersections.contains(intersection)) intersections.add(intersection);
    }

    // ── XÓA ──────────────────────────────────────────────────

    /**
     * Xóa một đoạn đường khỏi mạng lưới và ngắt kết nối tại các nút giao.
     * 
     * <p>Phương thức này thực hiện "dọn dẹp" bằng cách duyệt qua tất cả các 
     * nút giao hiện có để gỡ bỏ mọi tham chiếu đến đoạn đường bị xóa.</p>
     * 
     * @param segment Đoạn đường mục tiêu.
     * @throws IllegalArgumentException nếu không tìm thấy đoạn đường trong hệ thống.
     */
    public void removeSegment(RoadSegment segment) {
        if (!segments.remove(segment)) {
            throw new IllegalArgumentException("Không tìm thấy segment để xóa");
        }
        // Duyệt qua các Intersection để ngắt kết nối vật lý
        for (Intersection inter : intersections) {
            inter.disconnectRoad(segment);
        }
    }

    /**
     * Xóa một nút giao khỏi mạng lưới.
     * 
     * @param intersection Nút giao mục tiêu.
     * @throws IllegalArgumentException nếu không tìm thấy nút giao trong hệ thống.
     */
    public void removeIntersection(Intersection intersection) {
        if (!intersections.remove(intersection)) {
            throw new IllegalArgumentException("Không tìm thấy intersection để xóa");
        }
    }

    // ── SỬA ──────────────────────────────────────────────────

    /**
     * Thay thế một đoạn đường cũ bằng một phiên bản mới và cập nhật toàn mạng.
     * 
     * <p>Đây là phương thức quan trọng nhất để duy trì tính nhất quán. 
     * Ví dụ: Khi một con đường được nâng cấp từ 2 làn lên 4 làn, các nút giao 
     * đang nối vào con đường đó cần phải nhận biết được đối tượng đường mới này.</p>
     * 
     * @param oldSegment Đoạn đường hiện tại trong hệ thống.
     * @param newSegment Đoạn đường mới sẽ thay thế.
     */
    public void replaceSegment(RoadSegment oldSegment, RoadSegment newSegment) {
        int index = segments.indexOf(oldSegment);
        if (index == -1) throw new IllegalArgumentException("Không tìm thấy segment cần thay thế");
        
        segments.set(index, newSegment);
        
        // Cập nhật tất cả intersection đang giữ segment cũ để chúng trỏ sang segment mới
        for (Intersection inter : intersections) {
            inter.replaceRoad(oldSegment, newSegment);
        }
    }

    /**
     * Thay thế một nút giao bằng một phiên bản mới (ví dụ: nâng cấp Ngã tư thành Vòng xuyến).
     */
    public void replaceIntersection(Intersection oldIntersection, Intersection newIntersection) {
        int index = intersections.indexOf(oldIntersection);
        if (index == -1) throw new IllegalArgumentException("Không tìm thấy intersection cần thay thế");
        intersections.set(index, newIntersection);
    }

    // ── TRUY VẤN ─────────────────────────────────────────────

    /** Trả về danh sách đường (chế độ chỉ đọc để bảo mật dữ liệu). */
    public List<RoadSegment>  getSegments()      { return Collections.unmodifiableList(segments);      }
    
    /** Trả về danh sách nút giao (chế độ chỉ đọc). */
    public List<Intersection> getIntersections() { return Collections.unmodifiableList(intersections); }
    
    public int getSegmentCount()      { return segments.size();      }
    public int getIntersectionCount() { return intersections.size(); }

    /**
     * Tìm kiếm nút giao gần nhất với một tọa độ cụ thể.
     * 
     * <p>Sử dụng công thức khoảng cách Euclide: $d = \sqrt{(x_2-x_1)^2 + (y_2-y_1)^2}$. 
     * Hàm này cực kỳ hữu ích cho AI của xe để xác định các điểm rẽ sắp tới.</p>
     * 
     * @param x Tọa độ X cần kiểm tra.
     * @param y Tọa độ Y cần kiểm tra.
     * @param radius Bán kính quét tối đa (mét/pixel).
     * @return Nút giao gần nhất trong phạm vi, hoặc null nếu không có kết quả.
     */
    public Intersection findNearestIntersection(double x, double y, double radius) {
        Intersection nearest = null;
        double       minDist = Double.MAX_VALUE;
        
        for (Intersection inter : intersections) {
            double dx   = inter.getCenterX() - x;
            double dy   = inter.getCenterY() - y;
            double dist = Math.sqrt(dx * dx + dy * dy);
            
            if (dist < radius && dist < minDist) {
                minDist = dist;
                nearest = inter;
            }
        }
        return nearest;
    }

    @Override
    public String toString() {
        return String.format("RoadNetwork[Segments=%d, Intersections=%d]",
                segments.size(), intersections.size());
    }
}