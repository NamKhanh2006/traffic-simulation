package com.myteam.traffic.ui;

import com.myteam.traffic.model.infrastructure.*;
import com.myteam.traffic.model.infrastructure.intersection.GeneralIntersection;
import com.myteam.traffic.model.infrastructure.intersection.Intersection;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class SimulationView extends Canvas {

    public enum InteractionType { PAN, DRAW_ROAD, EDIT_MARKINGS, PLACE_INTERSECTION, DELETE }

    /**
     * Loại giao lộ sẽ được đặt khi người dùng click trong mode PLACE_INTERSECTION.
     * "3T","3Y"=Ngã ba T/Y; "4"=Ngã tư; "5"=Ngã năm; "ROUNDABOUT_S/L"=Vòng xuyến.
     */
    private String intersectionTypeToPlace = "4";

    private final InfrastructureRenderer renderer;
    private RoadNetwork network;
    private NetworkRenderData data;

    private double scale = 1.0;
    private double translateX = 0;
    private double translateY = 0;

    private boolean showGrid   = true;
    private boolean showLabels = true;
    private InteractionType currentMode = InteractionType.PAN;

    private int     currentLaneConfig = 4;
    private boolean autoIntersect     = true;

    private double  lastMouseX, lastMouseY;
    private boolean isDrawing = false;
    private double  drawStartX, drawStartY, drawCurrentX, drawCurrentY;

    private static final double SNAP_GRID = 100.0;

    // ── Hover state (EDIT_MARKINGS mode) ──────────────────────
    private HoverResult hoveredBoundary = null;

    // ── Hover state (DELETE mode) ──────────────────────────────
    private RoadSegment  hoveredSegment      = null;
    private Intersection hoveredIntersection = null;

    /** Gói dữ liệu mô tả ranh giới đang được hover. */
    private static class HoverResult {
        final RoadSegment seg;
        final int         boundaryIndex; // ranh giới nội bộ (1 .. laneCount-1)
        final double      screenX, screenY; // tọa độ màn hình của điểm giữa vạch

        HoverResult(RoadSegment seg, int boundaryIndex, double screenX, double screenY) {
            this.seg = seg;
            this.boundaryIndex = boundaryIndex;
            this.screenX = screenX;
            this.screenY = screenY;
        }
    }

    // ── Undo stack ────────────────────────────────────────────
    private final Stack<NetworkSnapshot> undoStack = new Stack<>();

    private static class NetworkSnapshot {
        List<RoadSegment>  roads;
        List<Intersection> inters;
        NetworkSnapshot(RoadNetwork net) {
            this.roads  = new ArrayList<>(net.getSegments());
            this.inters = new ArrayList<>(net.getIntersections());
        }
        void restore(RoadNetwork net) {
            net.clear();
            for (RoadSegment  r : roads)  net.addSegment(r);
            for (Intersection i : inters) net.addIntersection(i);
        }
    }

    public void saveSnapshot() { if (network != null) undoStack.push(new NetworkSnapshot(network)); }
    public void undo()         { if (!undoStack.isEmpty() && network != null) { undoStack.pop().restore(network); updateRenderData(); } }

    // ── Constructor ───────────────────────────────────────────
    public SimulationView(double width, double height) {
        super(width, height);
        this.renderer   = new InfrastructureRenderer(this);
        this.translateX = width  / 2;
        this.translateY = height / 2;
        setupEvents();
    }

    // ── Setters ───────────────────────────────────────────────
    public void setNetwork(RoadNetwork network)       { this.network = network; updateRenderData(); }
    public void setCurrentLaneConfig(int lanes)       { this.currentLaneConfig = lanes; }
    public void setAutoIntersect(boolean auto)        { this.autoIntersect = auto; }
    public void setShowGrid(boolean show)             { this.showGrid = show; redraw(); }
    public void setShowLabels(boolean show)           { this.showLabels = show; redraw(); }
    public void setIntersectionTypeToPlace(String t)  { this.intersectionTypeToPlace = t; }
    public void setInteractionType(InteractionType m) {
        this.currentMode = m;
        this.isDrawing   = false;
        this.hoveredBoundary = null; // reset hover khi đổi mode
        redraw();
    }

    private void updateRenderData() { this.data = (network != null) ? network.getRenderData() : null; redraw(); }
    public void resetView()  { scale = 1.0; translateX = getWidth()/2; translateY = getHeight()/2; redraw(); }
    public void zoomIn()     { scale *= 1.2; redraw(); }
    public void zoomOut()    { scale /= 1.2; redraw(); }
    public double getViewScale() { return scale; }
    public double getViewX()     { return translateX; }
    public double getViewY()     { return translateY; }

    // ── Coordinate transforms ─────────────────────────────────
    public double toScreenX(double worldX) { return worldX * scale + translateX; }
    public double toScreenY(double worldY) { return worldY * scale + translateY; }
    public double toWorldX(double screenX) { return (screenX - translateX) / scale; }
    public double toWorldY(double screenY) { return (screenY - translateY) / scale; }
    private double snap(double v)          { return Math.round(v / SNAP_GRID) * SNAP_GRID; }
    private boolean isSamePoint(double x1, double y1, double x2, double y2) {
        return Math.hypot(x1-x2, y1-y2) < 2.0;
    }
    private double getIntersectionRadiusAt(double worldX, double worldY) {
        if (data == null || data.intersections == null) return 0;
        double bestR = 0;
        double bestDist = Double.MAX_VALUE;
        for (IntersectionRenderData inter : data.intersections) {
            double d = Math.hypot(worldX - inter.centerX, worldY - inter.centerY);
            // Chấp nhận nếu điểm endpoint nằm trong hoặc sát bán kính nút giao
            if (d <= inter.radius + 5.0 && d < bestDist) {
                bestDist = d;
                bestR = inter.radius;
            }
        }
        return bestR;
    }



    /** Hit-test: tìm RoadSegment gần con trỏ nhất (dùng cho DELETE mode). */
    private RoadSegment hitTestSegment(double worldX, double worldY) {
        if (network == null) return null;
        RoadSegment result = null;
        double bestDist = Double.MAX_VALUE;
        for (RoadSegment seg : network.getSegments()) {
            double sx = seg.getStartX(), sy = seg.getStartY();
            double ex = seg.getEndX(),   ey = seg.getEndY();
            double dx = ex - sx, dy = ey - sy;
            double len2 = dx*dx + dy*dy;
            if (len2 < 1e-6) continue;
            double t = Math.max(0, Math.min(1, ((worldX-sx)*dx + (worldY-sy)*dy) / len2));
            double projX = sx + t*dx, projY = sy + t*dy;
            double d = Math.hypot(worldX - projX, worldY - projY);
            // Ngưỡng = nửa chiều rộng thực của đoạn đường (world units: số làn × 20 / 2)
            double halfWidth = seg.getLaneCount() * 20.0 / 2.0;
            if (d <= halfWidth && d < bestDist) { bestDist = d; result = seg; }
        }
        return result;
    }

    /**
     * Snap điểm world vào điểm gần nhất nằm TRÊN THÂN đoạn đường (không chỉ endpoint).
     * Trả về [snapX, snapY] nếu tìm thấy, null nếu không.
     * excludeX/excludeY: điểm xuất phát — bỏ qua nếu snap trùng với nó.
     */
    private double[] snapToSegmentPoint(double worldX, double worldY, double radius,
                                        double excludeX, double excludeY) {
        if (network == null) return null;
        double best = radius;
        double[] result = null;
        for (RoadSegment seg : network.getSegments()) {
            double sx = seg.getStartX(), sy = seg.getStartY();
            double ex = seg.getEndX(),   ey = seg.getEndY();
            double dx = ex - sx, dy = ey - sy;
            double len2 = dx*dx + dy*dy;
            if (len2 < 1e-6) continue;
            double t = ((worldX - sx)*dx + (worldY - sy)*dy) / len2;
            t = Math.max(0, Math.min(1, t));
            double projX = sx + t*dx, projY = sy + t*dy;
            if (isSamePoint(projX, projY, excludeX, excludeY)) continue;
            double d = Math.hypot(worldX - projX, worldY - projY);
            if (d < best) { best = d; result = new double[]{projX, projY}; }
        }
        return result;
    }

    /**
     * Tìm endpoint (điểm đầu/cuối) của đường hiện có gần nhất trong phạm vi radius.
     * Trả về [x, y] nếu tìm thấy, null nếu không.
     * excludeX/excludeY: điểm đang vẽ từ đó — không snap vào chính nó.
     */
    private double[] snapToEndpoint(double worldX, double worldY, double radius,
                                    double excludeX, double excludeY) {
        if (network == null) return null;
        double best = radius;
        double[] result = null;
        for (RoadSegment road : network.getSegments()) {
            double sx = road.getStartX(), sy = road.getStartY();
            double ex = road.getEndX(),   ey = road.getEndY();
            // Bỏ qua điểm trùng với điểm xuất phát (tránh snap vào chính điểm đang vẽ từ đó)
            if (!isSamePoint(sx, sy, excludeX, excludeY)) {
                double dS = Math.hypot(sx - worldX, sy - worldY);
                if (dS < best) { best = dS; result = new double[]{sx, sy}; }
            }
            if (!isSamePoint(ex, ey, excludeX, excludeY)) {
                double dE = Math.hypot(ex - worldX, ey - worldY);
                if (dE < best) { best = dE; result = new double[]{ex, ey}; }
            }
        }
        return result;
    }

    // ── Connection count (for stop lines) ────────────────────
    // Chỉ trả về số > 1 khi tại điểm đó có Intersection thật sự được đăng ký.
    // Tránh vẽ stop line khi 2 đường chỉ nối đuôi nhau thẳng hàng.
    private int countConnections(double x, double y) {
        if (network == null) return 0;
        // Kiểm tra có Intersection tại điểm này không
        Intersection inter = network.findNearestIntersection(x, y, 5.0);
        if (inter == null) return 1; // Không có ngã tư → không vẽ stop line
        // Đếm số đường kết nối vào intersection đó
        return inter.getRoadCount();
    }

    // ════════════════════════════════════════════════════════════
    // REDRAW
    // ════════════════════════════════════════════════════════════
    public void redraw() {
        GraphicsContext gc = getGraphicsContext2D();
        gc.setFill(Color.web("#1a1f2e"));
        gc.fillRect(0, 0, getWidth(), getHeight());

        if (showGrid) renderer.drawGrid(gc, getWidth(), getHeight());

        if (data != null) {
            for (RoadSegment road : data.roads) renderer.drawRoadBody(gc, road);

            for (IntersectionRenderData interData : data.intersections) {
                renderer.drawJunctionFill(gc, interData);
            }

            renderer.setRenderData(data); // inject để computeMarkClipOffset dùng
            for (RoadSegment road : data.roads) {
                renderer.drawRoadMarks(gc, road);
            }

            if (showLabels) {
                for (RoadSegment road : data.roads) renderer.drawRoadLabel(gc, road);
                for (IntersectionRenderData id : data.intersections) renderer.drawIntersectionLabel(gc, id);
            }

            if (currentMode == InteractionType.EDIT_MARKINGS && hoveredBoundary != null) {
                renderer.drawBoundaryHighlight(gc, hoveredBoundary.seg, hoveredBoundary.boundaryIndex);
            }

            // DELETE mode: highlight đỏ đoạn đường / nút giao đang hover
            if (currentMode == InteractionType.DELETE) {
                if (hoveredSegment != null) renderer.drawSegmentHighlight(gc, hoveredSegment);
                if (hoveredIntersection != null) renderer.drawIntersectionHighlight(gc, hoveredIntersection);
            }
        }

        if (currentMode == InteractionType.DRAW_ROAD && isDrawing) {
            double sx = toScreenX(drawStartX), sy = toScreenY(drawStartY);
            double ex = toScreenX(drawCurrentX), ey = toScreenY(drawCurrentY);

            // ── AXIS GUIDE: 2 đường trục theo hướng kéo vẽ ───────────────────
            // Tính góc hướng từ điểm bắt đầu → điểm hiện tại
            double axDx = ex - sx, axDy = ey - sy;
            double axLen = Math.hypot(axDx, axDy);
            if (axLen > 2) { // chỉ vẽ khi đã kéo đủ xa
                double ux = axDx / axLen, uy = axDy / axLen; // unit vector theo hướng vẽ
                double big = Math.max(getWidth(), getHeight()) * 3; // đủ dài ra ngoài màn hình

                gc.save();
                gc.setStroke(Color.web("#e8d44d", 0.28));
                gc.setLineWidth(1.2);
                gc.setLineDashes(14, 10);

                // Trục qua điểm BẮT ĐẦU — kéo cả 2 chiều
                gc.strokeLine(sx - ux * big, sy - uy * big, sx + ux * big, sy + uy * big);

                // Trục qua điểm HIỆN TẠI — kéo cả 2 chiều
                gc.strokeLine(ex - ux * big, ey - uy * big, ex + ux * big, ey + uy * big);

                gc.setLineDashes(null);
                gc.restore();
            }

            // ── PREVIEW đường đang vẽ ─────────────────────────────────────────
            gc.setStroke(Color.web("#e8d44d", 0.75));
            gc.setLineWidth(Math.max(2, (currentLaneConfig * 4) * scale));
            gc.setLineCap(StrokeLineCap.BUTT);
            gc.strokeLine(sx, sy, ex, ey);

            // Chấm nhỏ tại điểm bắt đầu và điểm hiện tại
            gc.setFill(Color.web("#e8d44d", 0.9));
            gc.fillOval(sx - 3, sy - 3, 6, 6);
            gc.fillOval(ex - 3, ey - 3, 6, 6);
        }

        // Ghost preview cho mode PLACE_INTERSECTION
        if (currentMode == InteractionType.PLACE_INTERSECTION && lastMouseX > 0) {
            double wx = snap(toWorldX(lastMouseX)), wy = snap(toWorldY(lastMouseY));
            double sx = toScreenX(wx), sy = toScreenY(wy);
            double previewR = getPreviewRadius() * scale;
            gc.setStroke(Color.web("#e8d44d", 0.55));
            gc.setLineWidth(2.5);
            gc.setLineDashes(8, 6);
            gc.strokeOval(sx - previewR, sy - previewR, previewR * 2, previewR * 2);
            gc.setLineDashes(null);
            gc.setFill(Color.web("#e8d44d", 0.18));
            gc.fillOval(sx - previewR, sy - previewR, previewR * 2, previewR * 2);
            gc.setFill(Color.web("#e8d44d", 0.9));
            gc.fillOval(sx - 4, sy - 4, 8, 8);
        }
    }

    // ════════════════════════════════════════════════════════════
    // HOVER HIT-TEST (EDIT_MARKINGS mode)
    // ════════════════════════════════════════════════════════════

    /**
     * Dò tìm ranh giới làn gần con trỏ nhất.
     * Trả về HoverResult nếu con trỏ đủ gần (< HOVER_THRESHOLD đơn vị thế giới),
     * ngược lại trả về null.
     *
     * Thuật toán: xoay điểm click vào hệ tọa độ cục bộ của đường,
     * sau đó so sánh localY với vị trí từng ranh giới.
     */
    private static final double HOVER_THRESHOLD = 8.0; // đơn vị thế giới

    private HoverResult hitTestBoundary(double worldX, double worldY) {
        if (network == null) return null;

        HoverResult best = null;
        double bestDist = HOVER_THRESHOLD;

        for (RoadSegment seg : network.getSegments()) {
            double sx = seg.getStartX(), sy = seg.getStartY();
            double ex = seg.getEndX(),   ey = seg.getEndY();
            double angle  = Math.atan2(ey - sy, ex - sx);
            double length = Math.hypot(ex - sx, ey - sy);

            double dx = worldX - sx, dy = worldY - sy;
            double localX =  dx * Math.cos(-angle) - dy * Math.sin(-angle);
            double localY =  dx * Math.sin(-angle) + dy * Math.cos(-angle);

            if (localX < -3.0 || localX > length + 3.0) continue;

            List<Lane> lanes = seg.getLanes();

            // SỬA LỖI: Chuyển đổi 3.5m thành 20 pixels thế giới
            double totalWorldWidth = lanes.stream().mapToDouble(l -> (l.getWidth() / 3.5) * 20.0).sum();

            if (Math.abs(localY) > totalWorldWidth / 2.0 + HOVER_THRESHOLD) continue;

            double boundaryPos = -totalWorldWidth / 2.0;
            for (int i = 0; i < lanes.size() - 1; i++) {
                boundaryPos += (lanes.get(i).getWidth() / 3.5) * 20.0; // Sử dụng scale 20.0

                double dist = Math.abs(localY - boundaryPos);
                if (dist < bestDist) {
                    bestDist = dist;
                    double midLocalX = length / 2.0;
                    double midWorldX = sx + midLocalX * Math.cos(angle) - boundaryPos * Math.sin(angle);
                    double midWorldY = sy + midLocalX * Math.sin(angle) + boundaryPos * Math.cos(angle);
                    best = new HoverResult(seg, i + 1, toScreenX(midWorldX), toScreenY(midWorldY));
                }
            }
        }
        return best;
    }

    // ════════════════════════════════════════════════════════════
    // POPUP MENU (EDIT_MARKINGS mode)
    // ════════════════════════════════════════════════════════════

    // ── Active context menu (để đóng khi cần) ─────────────────
    private ContextMenu activeMenu = null;

    private void hideActiveMenu() {
        if (activeMenu != null && activeMenu.isShowing()) {
            activeMenu.hide();
        }
        activeMenu = null;
    }

    /**
     * Hiển thị context menu để chọn loại vạch kẻ cho ranh giới đang hover.
     * Gọi khi chuột PHẢI click (hoặc chuột TRÁI nếu muốn).
     */
    private void showMarkingMenu(HoverResult target, double screenX, double screenY) {
        hideActiveMenu(); // Đóng menu cũ trước khi mở menu mới
        ContextMenu menu = new ContextMenu();
        menu.setStyle("-fx-background-color:#1e2838; -fx-border-color:#3a4a6a; -fx-border-width:1;");

        Lane currentMarking = target.seg.getLanes().get(target.boundaryIndex - 1);
        Lane.MarkingType current = currentMarking.getRightMarking();

        // Bổ sung toàn bộ các loại vạch từ model Lane.java
        addMarkingItem(menu, "  ╌╌╌  Nét đứt (Trắng)",         Lane.MarkingType.DASHED,       current, target);
        addMarkingItem(menu, "  ───  Nét liền (Trắng)",         Lane.MarkingType.SOLID,        current, target);
        addMarkingItem(menu, "  ═══  Nét đôi (Trắng)",          Lane.MarkingType.DOUBLE_SOLID, current, target);
        addMarkingItem(menu, "  ───  Nét liền (Vàng)",          Lane.MarkingType.YELLOW_SOLID, current, target);
        addMarkingItem(menu, "  ╌│─  Trái đứt / Phải liền",     Lane.MarkingType.LEFT_DASHED_RIGHT_SOLID, current, target);
        addMarkingItem(menu, "  ─│╌  Trái liền / Phải đứt",     Lane.MarkingType.LEFT_SOLID_RIGHT_DASHED, current, target);

        menu.getItems().add(new SeparatorMenuItem());
        addMarkingItem(menu, "  (trống) Xóa vạch", Lane.MarkingType.NONE, current, target);

        activeMenu = menu;
        menu.setOnHidden(ev -> { if (activeMenu == menu) activeMenu = null; });
        menu.show(this, screenX, screenY);
    }

    private void addMarkingItem(ContextMenu menu, String label,
                                Lane.MarkingType type, Lane.MarkingType current,
                                HoverResult target) {
        MenuItem item = new MenuItem((type == current ? "✔ " : "    ") + label);
        item.setStyle("-fx-text-fill:#c8d0e8; -fx-font-size:13px; -fx-background-color:transparent;");
        item.setOnAction(e -> applyMarking(target, type));
        menu.getItems().add(item);
    }

    private void applyMarking(HoverResult target, Lane.MarkingType type) {
        saveSnapshot();
        List<Lane> lanes    = target.seg.getLanes();
        List<Lane> newLanes = new ArrayList<>(lanes);
        Lane leftLane   = lanes.get(target.boundaryIndex - 1);
        Lane rightLane  = lanes.get(target.boundaryIndex);
        newLanes.set(target.boundaryIndex - 1, leftLane.withRightMarking(type));
        newLanes.set(target.boundaryIndex,     rightLane.withLeftMarking(type));
        network.replaceSegment(target.seg, target.seg.withNewLanes(newLanes));
        // Không set null — redraw sẽ trigger MOUSE_MOVED re-detect hover tự động
        // Nhưng vì segment đã thay thế, cần clear để tránh stale reference
        hoveredBoundary = null;
        updateRenderData();
        // Tái tạo hover trên segment mới ngay lập tức (không cần di chuyển chuột)
        hoveredBoundary = hitTestBoundary(
                toWorldX(target.screenX), toWorldY(target.screenY));
        redraw();
    }

    // ════════════════════════════════════════════════════════════
    // INTERSECTION HIT-TEST HELPERS
    // ════════════════════════════════════════════════════════════
    private double[] getIntersectionPointStrict(double x1, double y1, double x2, double y2,
                                                double x3, double y3, double x4, double y4) {
        double denom = (x1-x2)*(y3-y4) - (y1-y2)*(x3-x4);
        if (denom == 0) return null;
        double t = ((x1-x3)*(y3-y4) - (y1-y3)*(x3-x4)) / denom;
        double u = -((x1-x2)*(y1-y3) - (y1-y2)*(x1-x3)) / denom;
        // Yêu cầu giao điểm phải nằm TRONG đoạn (không tính endpoint của đường đang vẽ)
        // và nằm TRONG đoạn existing (không tính endpoint — tránh tạo intersection giả khi kéo dài)
        boolean tInside = t > 0.01 && t < 0.99;   // điểm trên đường đang vẽ (không tính 2 đầu)
        boolean uInside = u > 0.01 && u < 0.99;   // điểm nằm thực sự giữa đường existing
        if (tInside && uInside)
            return new double[]{x1 + t*(x2-x1), y1 + t*(y2-y1)};
        // Cho phép t bao gồm điểm đầu/cuối của đường đang vẽ, nhưng u phải là midpoint thật sự
        if (t >= -0.01 && t <= 1.01 && uInside)
            return new double[]{x1 + t*(x2-x1), y1 + t*(y2-y1)};
        return null;
    }

    private static class HitGroup {
        double x, y, dist;
        List<RoadSegment> crossedSegments = new ArrayList<>();
        boolean isExistingIntersection = false;
        Intersection existingIntersection = null;
        HitGroup(double x, double y, double dist) { this.x = x; this.y = y; this.dist = dist; }
    }

    // ════════════════════════════════════════════════════════════
    // MOUSE EVENTS
    // ════════════════════════════════════════════════════════════
    private void setupEvents() {

        // Scroll = zoom tại vị trí con trỏ
        addEventHandler(ScrollEvent.SCROLL, e -> {
            double delta = e.getDeltaY() > 0 ? 1.1 : 0.9;
            double wx = toWorldX(e.getX()), wy = toWorldY(e.getY());
            scale *= delta;
            translateX = e.getX() - wx * scale;
            translateY = e.getY() - wy * scale;
            redraw();
        });

        // MOUSE_MOVED — hover highlight (chỉ EDIT mode, không kéo)
        addEventHandler(MouseEvent.MOUSE_MOVED, e -> {
            double wx = toWorldX(e.getX()), wy = toWorldY(e.getY());
            if (currentMode == InteractionType.EDIT_MARKINGS) {
                HoverResult hit = hitTestBoundary(wx, wy);
                if (hit != hoveredBoundary) { hoveredBoundary = hit; redraw(); }
            } else if (currentMode == InteractionType.PLACE_INTERSECTION) {
                lastMouseX = e.getX(); lastMouseY = e.getY(); redraw();
            } else if (currentMode == InteractionType.DELETE) {
                RoadSegment  newSeg   = hitTestSegment(wx, wy);
                Intersection newInter = (newSeg == null)
                        ? network.findNearestIntersection(wx, wy, 30.0 / scale) : null;
                if (newSeg != hoveredSegment || newInter != hoveredIntersection) {
                    hoveredSegment = newSeg; hoveredIntersection = newInter; redraw();
                }
            }
        });

        // MOUSE_PRESSED
        addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            if (e.getButton() == MouseButton.SECONDARY) {
                // Chuột PHẢI trong EDIT mode + hover vạch → hiện menu
                if (currentMode == InteractionType.EDIT_MARKINGS && hoveredBoundary != null) {
                    showMarkingMenu(hoveredBoundary, e.getScreenX(), e.getScreenY());
                } else {
                    hideActiveMenu(); // đóng menu nếu đang mở
                    lastMouseX = e.getX(); lastMouseY = e.getY();
                }
                return;
            }
            if (e.getButton() != MouseButton.PRIMARY) return;
            hideActiveMenu(); // click trái bất kỳ đâu → đóng menu

            if (currentMode == InteractionType.PAN) {
                lastMouseX = e.getX(); lastMouseY = e.getY();

            } else if (currentMode == InteractionType.DELETE) {
                if (hoveredSegment != null) {
                    saveSnapshot();
                    network.removeSegment(hoveredSegment);
                    hoveredSegment = null; updateRenderData();
                } else if (hoveredIntersection != null) {
                    saveSnapshot();
                    network.removeIntersection(hoveredIntersection);
                    hoveredIntersection = null; updateRenderData();
                }

            } else if (currentMode == InteractionType.PLACE_INTERSECTION) {
                double rawX = toWorldX(e.getX()), rawY = toWorldY(e.getY());
                double wx = snap(rawX), wy = snap(rawY);
                saveSnapshot();
                placeIntersectionAt(wx, wy);
                updateRenderData();

            } else if (currentMode == InteractionType.DRAW_ROAD) {
                saveSnapshot();
                double rawX = toWorldX(e.getX()), rawY = toWorldY(e.getY());
                // Ưu tiên 1: snap vào endpoint của đường hiện có (không loại trừ gì ở bước press)
                double[] epSnap = snapToEndpoint(rawX, rawY, 60.0 / scale, Double.MAX_VALUE, Double.MAX_VALUE);
                if (epSnap != null) {
                    drawStartX = epSnap[0]; drawStartY = epSnap[1];
                    // Ưu tiên 2: snap vào tâm intersection
                } else {
                    Intersection mag = network.findNearestIntersection(rawX, rawY, 60.0 / scale);
                    if (mag != null) { drawStartX = mag.getCenterX(); drawStartY = mag.getCenterY(); }
                    else             { drawStartX = snap(rawX);        drawStartY = snap(rawY); }
                }
                drawCurrentX = drawStartX; drawCurrentY = drawStartY;
                isDrawing = true; redraw();
            }
            // EDIT_MARKINGS: chuột trái không làm gì (menu chỉ qua chuột phải)
        });

        // MOUSE_DRAGGED
        addEventHandler(MouseEvent.MOUSE_DRAGGED, e -> {
            if (e.getButton() == MouseButton.SECONDARY) {
                translateX += e.getX() - lastMouseX;
                translateY += e.getY() - lastMouseY;
                lastMouseX = e.getX(); lastMouseY = e.getY();
                redraw();
                return;
            }
            if (e.getButton() != MouseButton.PRIMARY) return;

            if (currentMode == InteractionType.PAN) {
                translateX += e.getX() - lastMouseX;
                translateY += e.getY() - lastMouseY;
                lastMouseX = e.getX(); lastMouseY = e.getY();
                redraw();

            } else if (currentMode == InteractionType.DRAW_ROAD && isDrawing) {
                double rawX = toWorldX(e.getX()), rawY = toWorldY(e.getY());
                double[] epSnap = snapToEndpoint(rawX, rawY, 60.0 / scale, drawStartX, drawStartY);
                if (epSnap != null) { drawCurrentX = epSnap[0]; drawCurrentY = epSnap[1]; }
                else {
                    // Snap vào intersection
                    Intersection mag = network.findNearestIntersection(rawX, rawY, 60.0 / scale);
                    if (mag != null) { drawCurrentX = mag.getCenterX(); drawCurrentY = mag.getCenterY(); }
                    else {
                        // Snap vào điểm bất kỳ trên thân đường
                        double[] segSnap = snapToSegmentPoint(rawX, rawY, 40.0 / scale, drawStartX, drawStartY);
                        if (segSnap != null) { drawCurrentX = segSnap[0]; drawCurrentY = segSnap[1]; }
                        else { drawCurrentX = snap(rawX); drawCurrentY = snap(rawY); }
                    }
                }
                redraw();
            }
        });

        // MOUSE_RELEASED
        addEventHandler(MouseEvent.MOUSE_RELEASED, e -> {
            if (e.getButton() != MouseButton.PRIMARY) return;
            if (currentMode != InteractionType.DRAW_ROAD || !isDrawing) return;

            isDrawing = false;
            if (isSamePoint(drawStartX, drawStartY, drawCurrentX, drawCurrentY)) {
                undoStack.pop(); redraw(); return;
            }

            // Nếu số làn > 8: tạo vòng xuyến thay vì đường thẳng
            if (currentLaneConfig > 8) {
                placeRoundabout(drawStartX, drawStartY, drawCurrentX, drawCurrentY);
                updateRenderData();
                return;
            }

            List<Lane> newLanes = SimulationApp.createLanes(currentLaneConfig);

            if (autoIntersect) {
                commitWithIntersections(drawStartX, drawStartY, drawCurrentX, drawCurrentY, newLanes);
            } else {
                network.addSegment(new RoadSegment(drawStartX, drawStartY, drawCurrentX, drawCurrentY, newLanes));
            }
            updateRenderData();
        });
    }

    /**
     * Tạo đường mới từ (x1,y1) đến (x2,y2), tự động cắt và tạo Intersection
     * tại mọi điểm giao với đường hiện có.
     *
     * Quy tắc quan trọng:
     * - Nếu giao điểm nằm gần một Intersection có sẵn → đường mới đi QUA intersection đó,
     *   KHÔNG tạo intersection mới, KHÔNG cắt thêm các segment trong intersection đó.
     * - Nếu giao điểm nằm giữa một RoadSegment thông thường → cắt đôi, tạo intersection mới.
     */
    private void commitWithIntersections(double x1, double y1, double x2, double y2, List<Lane> newLanes) {
        List<RoadSegment> snapshot = new ArrayList<>(network.getSegments());

        // ── Bước 1: Thu thập hit points ──────────────────────────────────────
        // Mỗi "hit" là 1 điểm trên đường đang vẽ mà nó cắt qua đường existing.
        // Key insight: nếu điểm cắt nằm gần intersection có sẵn → snap vào tâm intersection đó,
        // và đánh dấu là "existing intersection" (không phải midpoint cut).

        // Map từ tọa độ → HitGroup
        List<HitGroup> hits = new ArrayList<>();

        for (RoadSegment existing : snapshot) {
            double[] p = getIntersectionPointStrict(
                    x1, y1, x2, y2,
                    existing.getStartX(), existing.getStartY(),
                    existing.getEndX(),   existing.getEndY());
            if (p == null) continue;

            double cx = p[0], cy = p[1];

            // Bỏ qua nếu trùng điểm bắt đầu/kết thúc của đường đang vẽ
            if (isSamePoint(cx, cy, x1, y1) || isSamePoint(cx, cy, x2, y2)) continue;

            // Tìm intersection có sẵn gần điểm cắt (snap radius lớn hơn: 60 world units)
            Intersection nearInter = network.findNearestIntersection(cx, cy, 60.0);
            boolean isExistingIntersection = (nearInter != null);
            if (isExistingIntersection) {
                cx = nearInter.getCenterX();
                cy = nearInter.getCenterY();
            }

            // Gom vào HitGroup cùng tọa độ
            final double fcx = cx, fcy = cy;
            HitGroup tgt = hits.stream()
                    .filter(g -> isSamePoint(g.x, g.y, fcx, fcy))
                    .findFirst().orElse(null);
            if (tgt == null) {
                tgt = new HitGroup(cx, cy, Math.hypot(cx - x1, cy - y1));
                tgt.isExistingIntersection = isExistingIntersection;
                tgt.existingIntersection   = nearInter;
                hits.add(tgt);
            }
            // Nếu là existing intersection thì không cần cắt segment nào
            if (!isExistingIntersection) {
                tgt.crossedSegments.add(existing);
            }
        }

        if (hits.isEmpty()) {
            network.addSegment(new RoadSegment(x1, y1, x2, y2, newLanes));
            return;
        }

        hits.sort((a, b) -> Double.compare(a.dist, b.dist));

        // ── Bước 2: Tạo đường theo từng hit ──────────────────────────────────
        double curX = x1, curY = y1;
        GeneralIntersection lastNode = null;

        for (HitGroup group : hits) {
            if (isSamePoint(curX, curY, group.x, group.y)) continue;

            // Tạo đoạn đường từ vị trí hiện tại đến điểm giao
            RoadSegment inPart = new RoadSegment(curX, curY, group.x, group.y, newLanes);
            network.addSegment(inPart);
            if (lastNode != null) lastNode.connectRoad(inPart, ConnectionPoint.End.START);

            GeneralIntersection workNode;

            if (group.isExistingIntersection && group.existingIntersection != null) {
                // Đi qua intersection có sẵn → mở rộng nó thêm 2 nhánh (vào/ra)
                Intersection old = group.existingIntersection;
                int newCap = Math.max(old.getRoadCount() + 2, 8);
                workNode = new GeneralIntersection(group.x, group.y, newCap);
                for (ConnectionPoint cp : old.getConnections())
                    workNode.connectRoad(cp.getSegment(), cp.getEnd());
                network.removeIntersection(old);
                network.addIntersection(workNode);
                // Cập nhật tham chiếu trong các hit tiếp theo nếu chúng cũng snap vào cùng intersection
                for (HitGroup later : hits) {
                    if (later.existingIntersection == old) later.existingIntersection = null; // đã xử lý
                }
            } else {
                // Cắt giữa chừng đường existing → tạo intersection mới
                workNode = getOrCreateIntersection(group.x, group.y, group.crossedSegments.size());

                for (RoadSegment crossed : group.crossedSegments) {
                    boolean atStart = isSamePoint(crossed.getStartX(), crossed.getStartY(), group.x, group.y);
                    boolean atEnd   = isSamePoint(crossed.getEndX(),   crossed.getEndY(),   group.x, group.y);
                    if (atStart || atEnd) continue;
                    if (!network.getSegments().contains(crossed)) continue;

                    network.removeSegment(crossed);
                    RoadSegment p1 = new RoadSegment(crossed.getStartX(), crossed.getStartY(), group.x, group.y, crossed.getLanes());
                    RoadSegment p2 = new RoadSegment(group.x, group.y, crossed.getEndX(), crossed.getEndY(), crossed.getLanes());
                    network.addSegment(p1);
                    network.addSegment(p2);
                    workNode.connectRoad(p1, ConnectionPoint.End.END);
                    workNode.connectRoad(p2, ConnectionPoint.End.START);
                }
            }

            workNode.connectRoad(inPart, ConnectionPoint.End.END);
            lastNode = workNode;
            curX = group.x; curY = group.y;
        }

        // Đoạn cuối
        if (!isSamePoint(curX, curY, x2, y2)) {
            RoadSegment tail = new RoadSegment(curX, curY, x2, y2, newLanes);
            network.addSegment(tail);
            if (lastNode != null) lastNode.connectRoad(tail, ConnectionPoint.End.START);
        }
    }

    /**
     * Lấy Intersection hiện có tại (cx, cy) hoặc tạo mới.
     * capacity = số đường existing bị cắt + 2 (đường mới vào và ra).
     * Luôn dùng capacity đủ lớn (tối thiểu 8) để tránh lỗi "vượt quá số đường".
     */
    private GeneralIntersection getOrCreateIntersection(double cx, double cy, int crossedCount) {
        Intersection existNode = network.findNearestIntersection(cx, cy, 5.0);
        if (existNode != null) {
            int oldCount = existNode.getRoadCount();
            // capacity = số nhánh cũ + 2 nhánh mới (vào/ra) + số đường bị cắt thêm * 2
            int newCap = Math.max(oldCount + crossedCount * 2 + 2, 8);
            GeneralIntersection workNode = new GeneralIntersection(cx, cy, newCap);
            for (ConnectionPoint cp : existNode.getConnections())
                workNode.connectRoad(cp.getSegment(), cp.getEnd());
            network.removeIntersection(existNode);
            network.addIntersection(workNode);
            return workNode;
        } else {
            // capacity đủ lớn cho mọi trường hợp
            int cap = Math.max(crossedCount * 2 + 2, 8);
            GeneralIntersection workNode = new GeneralIntersection(cx, cy, cap);
            network.addIntersection(workNode);
            return workNode;
        }
    }

    /**
     * Đặt vòng xuyến khi dùng DRAW_ROAD với laneConfig > 8.
     */
    private void placeRoundabout(double x1, double y1, double x2, double y2) {
        double cx = (x1 + x2) / 2.0;
        double cy = (y1 + y2) / 2.0;
        double dist = Math.hypot(x2 - x1, y2 - y1);
        double radius = Math.max(60.0, dist / 2.0);
        com.myteam.traffic.model.infrastructure.intersection.RoundaboutIntersection roundabout =
                new com.myteam.traffic.model.infrastructure.intersection.RoundaboutIntersection(cx, cy, 6, radius);
        network.addIntersection(roundabout);
    }

    /**
     * Đặt giao lộ tại tọa độ world (wx, wy) theo loại đang được chọn.
     * Nếu đã có giao lộ gần đó (trong 40 world units) thì không tạo mới.
     */
    private void placeIntersectionAt(double wx, double wy) {
        if (network.findNearestIntersection(wx, wy, 40.0) != null) return;

        com.myteam.traffic.model.infrastructure.intersection.Intersection inter;
        switch (intersectionTypeToPlace) {
            case "3"  -> inter = new com.myteam.traffic.model.infrastructure.intersection.ThreeWayIntersection(wx, wy);
            case "5"  -> inter = new com.myteam.traffic.model.infrastructure.intersection.FiveWayIntersection(wx, wy);
            case "ROUNDABOUT_S" -> inter = new com.myteam.traffic.model.infrastructure.intersection.RoundaboutIntersection(wx, wy, 4, 60);
            case "ROUNDABOUT_L" -> inter = new com.myteam.traffic.model.infrastructure.intersection.RoundaboutIntersection(wx, wy, 6, 100);
            default -> inter = new com.myteam.traffic.model.infrastructure.intersection.FourWayIntersection(wx, wy);
        }
        network.addIntersection(inter);
    }

    private double getPreviewRadius() {
        return switch (intersectionTypeToPlace) {
            case "3"            -> 42;
            case "5"            -> 55;
            case "ROUNDABOUT_S" -> 60;
            case "ROUNDABOUT_L" -> 100;
            default -> 48;
        };
    }
}