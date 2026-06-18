package com.myteam.traffic.ui;

import com.myteam.traffic.model.infrastructure.*;
import com.myteam.traffic.model.infrastructure.intersection.GeneralIntersection;
import com.myteam.traffic.model.infrastructure.intersection.Intersection;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.stage.Popup;

import com.myteam.traffic.controller.VehicleSpawner;
import com.myteam.traffic.vehicle.Vehicle;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class SimulationView extends Canvas {

    public enum InteractionType { PAN, DRAW_ROAD, EDIT_MARKINGS, PLACE_INTERSECTION, DELETE, DELETE_LIGHT }

    /**
     * Loại giao lộ sẽ được đặt khi người dùng click trong mode PLACE_INTERSECTION.
     * "3T","3Y"=Ngã ba T/Y; "4"=Ngã tư; "5"=Ngã năm; "ROUNDABOUT_S/L"=Vòng xuyến.
     */
    private String intersectionTypeToPlace = "4";
    private double roundaboutRadius        = 60.0;
    private int    roundaboutBranches      = 4;
    private java.util.function.IntFunction<java.util.List<Lane>> laneFactory = null;

    // ── Cấu hình làn nâng cao ──────────────────────────────────
    private double  laneWidth       = 3.5;
    private boolean highwayMode     = false;
    private double  highwayMinSpeed = 60.0;
    private boolean emergencyLane   = false;
    private boolean allowHeavy      = false;

    private final InfrastructureRenderer renderer;
    private RoadNetwork network;
    private NetworkRenderData data;

    private final TrafficLightRenderer lightRenderer = new TrafficLightRenderer(this);

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

    private boolean isGraphicMode = true;

    public void setGraphicMode(boolean b) {
        this.isGraphicMode = b;
        redraw();
    }
    public boolean isGraphicMode() { return isGraphicMode; }

    private com.myteam.traffic.controller.TrafficController controller;
    private final VehicleRenderer vehicleRenderer = new VehicleRenderer();

    // ── Mô phỏng (spawn + tick) ────────────────────────────────
    private VehicleSpawner spawner;
    private boolean simulationRunning = false;
    private boolean deleteVehicleMode = false;
    private Vehicle hoveredVehicle = null;
    private static final double FIXED_DT = 0.033;  // 30 ticks/second

    private final javafx.animation.AnimationTimer simLoop = new javafx.animation.AnimationTimer() {
        private long lastNanos = -1;
        private double accumulator = 0.0;
        
        @Override public void handle(long now) {
            if (lastNanos < 0) {
                lastNanos = now;
                return;
            }
            double frameTime = (now - lastNanos) / 1_000_000_000.0;
            lastNanos = now;
            if (frameTime > 0.25) frameTime = 0.25; // giới hạn tránh vòng lặp quá dài
            accumulator += frameTime;
            while (simulationRunning && accumulator >= FIXED_DT) {
                if (spawner != null) spawner.tick(FIXED_DT);
                if (controller != null) controller.tick(FIXED_DT);
                    accumulator -= FIXED_DT;
            }
            redraw();
        }
    };

    public void setSpawner(VehicleSpawner spawner) {
        this.spawner = spawner;
        vehicleRenderer.setSpawner(spawner);  // ← thêm dòng này để fade-in hoạt động
    }
    public VehicleSpawner getSpawner() { return spawner; }
    public com.myteam.traffic.controller.TrafficController getController() { return controller; }

    /** Bật/tắt vòng lặp mô phỏng (tick controller + spawner mỗi frame). */
    public void setSimulationRunning(boolean run) {
        this.simulationRunning = run;
        if (run) simLoop.start(); else simLoop.stop();
    }
    public boolean isSimulationRunning() { return simulationRunning; }

    public void setDeleteVehicleMode(boolean b) { 
        this.deleteVehicleMode = b; 
        if (!b) hoveredVehicle = null; 
        redraw(); 
    }

    /** Chạy 1 tick thủ công (vd. ngay sau khi thêm xe lúc đang pause) để cập nhật hiển thị. */
    public void stepOnce() {
        if (controller != null) controller.tick(FIXED_DT);   // dùng cùng deltaTime
        redraw();
    }

    // ── Redraw throttle: gộp nhiều yêu cầu vẽ lại trong 1 frame ──
    private boolean redrawPending = false;
    private final javafx.animation.AnimationTimer redrawTimer = new javafx.animation.AnimationTimer() {
        @Override public void handle(long now) {
            if (redrawPending) { redrawPending = false; redrawNow(); }
        }
    };

    private static final double SNAP_GRID = 100.0;

    // ── Hover state (EDIT_MARKINGS mode) ───────────────────────
    private HoverResult hoveredBoundary = null;

    // ── Hover state (DELETE mode) ──────────────────────────────
    private RoadSegment  hoveredSegment      = null;
    private Intersection hoveredIntersection = null;

    // ── Hover state (DELETE_LIGHT mode) ──────────────────────────────
    private com.myteam.traffic.light.SegmentLight hoveredLight = null;

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

    /**
     * Callback để App đồng bộ toolbar khi view thay đổi trạng thái EDIT_MARKINGS.
     * Tham số: InteractionType trước đó nếu vừa vào EDIT_MARKINGS, null nếu vừa thoát.
     */
    private java.util.function.Consumer<InteractionType> onMarkingModeEntered = null;
    public void setOnMarkingModeEntered(java.util.function.Consumer<InteractionType> cb) { this.onMarkingModeEntered = cb; }

    /** Mode trước khi vào EDIT_MARKINGS — để restore khi thoát. */
    private InteractionType previousMode = InteractionType.PAN;

    /**
     * Flag bỏ qua 1 MOUSE_PRESSED tiếp theo — dùng khi popup đóng và event
     * "click để đóng popup" bị forward xuống canvas gây pan/select không mong muốn.
     */
    private boolean ignoreNextPress = false;
    private final java.util.Deque<Runnable> undoStack = new java.util.ArrayDeque<>();

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

    public void saveSnapshot() { 
        if (network != null) {
            NetworkSnapshot snap = new NetworkSnapshot(network);
            undoStack.push(() -> { snap.restore(network); updateRenderData(); });
        } 
    }
    public void undo() { 
        if (!undoStack.isEmpty() && network != null) { 
            undoStack.pop().run(); 
            redraw();
        } 
    }

    // ── Constructor ──────────────────────────────────────────
    public SimulationView(double width, double height) {
        super(width, height);
        this.renderer   = new InfrastructureRenderer(this);
        this.translateX = width  / 2;
        this.translateY = height / 2;
        setupEvents();
        // Ẩn popup khi cửa sổ mất focus
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.windowProperty().addListener((obs2, ow, nw) -> {
                    if (nw != null) nw.focusedProperty().addListener((obs3, was, focused) -> {
                        if (!focused) hideRoadTip();
                    });
                });
            }
        });
    }

    // ── Setters ──────────────────────────────────────────────
    public void setNetwork(RoadNetwork network)       { this.network = network; updateRenderData(); }
    public RoadNetwork getNetwork()                   { return network; }
    public void setCurrentLaneConfig(int lanes)       { this.currentLaneConfig = lanes; }
    public void setAutoIntersect(boolean auto)        { this.autoIntersect = auto; }
    public void setShowGrid(boolean show)             { this.showGrid = show; redraw(); }
    public void setShowLabels(boolean show)           { this.showLabels = show; redraw(); }
    public void setIntersectionTypeToPlace(String t)  { this.intersectionTypeToPlace = t; }
    public void setLaneFactory(java.util.function.IntFunction<java.util.List<Lane>> f) { this.laneFactory = f; }

    // ── Cấu hình làn đường nâng cao ──────────────────────────
    /** Chiều rộng mỗi làn (mét, mặc định 3.5). Áp dụng cho đường vẽ tiếp theo. */
    public void setLaneWidth(double w)                { this.laneWidth = Math.max(2.0, Math.min(6.0, w)); }
    /** Bán kính vòng xuyến (world units). */
    public void setRoundaboutRadius(double r)         { this.roundaboutRadius = Math.max(40, r); }
    // ── Selected segment (click trong PAN mode để chọn đường sửa) ──
    private RoadSegment selectedSegment = null;
    private java.util.function.Consumer<RoadSegment> onSegmentSelected = null;
    public void setOnSegmentSelected(java.util.function.Consumer<RoadSegment> cb) { this.onSegmentSelected = cb; }

    public RoadSegment getSelectedSegment()  { return selectedSegment; }
    public RoadSegment getHoveredSegment()   { return tipSegment; }

    public void clearSelectedSegment() { selectedSegment = null; redraw(); }
    public void replaceSegment(RoadSegment oldSeg, RoadSegment newSeg) {
        if (network == null) return;
        saveSnapshot();
        network.replaceSegment(oldSeg, newSeg);
        if (selectedSegment == oldSeg) selectedSegment = newSeg;
        updateRenderData();
        redraw();
    }
    /** Số nhánh vòng xuyến. */
    public void setRoundaboutBranches(int b)          { this.roundaboutBranches = Math.max(3, Math.min(8, b)); }
    /** Đường cao tốc: true = dùng HighwaySegment. */
    public void setHighwayMode(boolean hw)            { this.highwayMode = hw; }
    /** Tốc độ tối thiểu cao tốc (km/h). */
    public void setHighwayMinSpeed(double s)          { this.highwayMinSpeed = s; }
    /** Có làn khẩn cấp không. */
    public void setEmergencyLane(boolean el)          { this.emergencyLane = el; }
    /** Cho phép xe buýt/xe tải. */
    public void setAllowHeavy(boolean allow)          { this.allowHeavy = allow; }

    public void setInteractionType(InteractionType m) {
        this.currentMode = m;
        this.isDrawing   = false;
        this.hoveredBoundary    = null;
        this.hoveredSegment     = null;
        this.hoveredIntersection = null;
        this.hoveredLight       = null;
        // Đặt vị trí preview về tâm canvas nếu chuột chưa vào canvas
        if (lastMouseX <= 0) lastMouseX = getWidth() / 2;
        if (lastMouseY <= 0) lastMouseY = getHeight() / 2;
        redraw();
    }

    public void updateRenderData() { this.data = (network != null) ? network.getRenderData() : null; redraw(); }
    public void resetView()  { scale = 1.0; translateX = getWidth()/2; translateY = getHeight()/2; redraw(); }
    public void zoomIn()     { scale *= 1.2; redraw(); }
    public void zoomOut()    { scale /= 1.2; redraw(); }

    /** Yêu cầu vẽ lại — được gộp lại trong 1 frame, tránh vẽ nhiều lần/frame khi zoom/pan nhanh. */
    public void redraw() { redrawPending = true; redrawTimer.start(); }

    public double getViewScale() { return scale; }
    public InteractionType getCurrentMode() { return currentMode; }
    public double getViewX()     { return translateX; }
    public double getViewY()     { return translateY; }

    // ── Coordinate transforms ────────────────────────────────
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
            double t = ((worldX-sx)*dx + (worldY-sy)*dy) / len2;
            // Lùi vào trong 8% ở 2 đầu để tránh đè vùng giao lộ
            t = Math.max(0.08, Math.min(0.92, t));
            double projX = sx + t*dx, projY = sy + t*dy;
            double d = Math.hypot(worldX - projX, worldY - projY);
            // Vùng nhận diện = 60% chiều rộng thực (rộng hơn cũ ×1.4 nhưng không quá toàn bộ)
            double halfWidth = seg.getLaneCount() * 20.0 / 2.0 * 1.4;
            if (d <= halfWidth && d < bestDist) { bestDist = d; result = seg; }
        }
        return result;
    }

    private double[] getLightPosition(com.myteam.traffic.light.SegmentLight sl) {
        double ang = sl.getAngle();
        double baseWx = sl.getWorldX();
        double baseWy = sl.getWorldY();

        double interRadius = 30.0;
        if (data != null && data.intersections != null) {
            for (com.myteam.traffic.model.infrastructure.IntersectionRenderData ird : data.intersections) {
                if (Math.hypot(baseWx - ird.centerX, baseWy - ird.centerY) < 5.0) {
                    interRadius = ird.radius;
                    break;
                }
            }
        }
        double dirSign = sl.isAtEnd() ? -1.0 : 1.0;
        double stoplineWx = baseWx + dirSign * interRadius * Math.cos(ang);
        double stoplineWy = baseWy + dirSign * interRadius * Math.sin(ang);

        double sideAng = sl.isAtEnd() ? (ang - Math.PI / 2) : (ang + Math.PI / 2);
        double sideOffset = sl.getSegment().getLaneCount() * 20.0 / 2.0 + 8.0;
        double wx = stoplineWx + Math.cos(sideAng) * sideOffset;
        double wy = stoplineWy + Math.sin(sideAng) * sideOffset;
        return new double[]{wx, wy};
    }

    private com.myteam.traffic.light.SegmentLight hitTestLight(double worldX, double worldY) {
        if (controller == null) return null;
        for (com.myteam.traffic.light.SegmentLight sl : controller.getAllSegmentLights()) {
            double[] pos = getLightPosition(sl);
            if (Math.hypot(worldX - pos[0], worldY - pos[1]) < 18.0) {
                return sl;
            }
        }
        return null;
    }

    /**
     * Phiên bản hit-test rộng hơn dùng cho hover + chuột phải pin popup.
     * Vùng nhận diện = toàn bộ chiều rộng đường + padding 12 world units mỗi bên,
     * và không bị giới hạn 8% ở 2 đầu (để hover đầu đường vẫn hiện popup).
     */
    private RoadSegment hitTestSegmentForPopup(double worldX, double worldY) {
        if (network == null) return null;
        RoadSegment result = null;
        double bestDist = Double.MAX_VALUE;
        for (RoadSegment seg : network.getSegments()) {
            if (seg.isConnector()) continue;
            double sx = seg.getStartX(), sy = seg.getStartY();
            double ex = seg.getEndX(),   ey = seg.getEndY();
            double dx = ex - sx, dy = ey - sy;
            double len2 = dx*dx + dy*dy;
            if (len2 < 1e-6) continue;
            double t = ((worldX-sx)*dx + (worldY-sy)*dy) / len2;
            t = Math.max(0.0, Math.min(1.0, t)); // toàn bộ chiều dài đường
            double projX = sx + t*dx, projY = sy + t*dy;
            double d = Math.hypot(worldX - projX, worldY - projY);
            // Toàn bộ nửa chiều rộng thực + 12 world units padding
            double halfWidth = seg.getLaneCount() * laneWidth / 2.0 + 12.0;
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
    // Chỉ trả về số > 1 khi tại điểm đó có Intersection thực sự được đăng ký.
    // Tránh vẽ stop line khi 2 đường chỉ nối đuôi nhau thẳng hàng.
    private int countConnections(double x, double y) {
        if (network == null) return 0;
        // Kiểm tra có Intersection tại điểm này không
        Intersection inter = network.findNearestIntersection(x, y, 5.0);
        if (inter == null) return 1; // Không có ngã tư → không vẽ stop line
        // Đếm số đường kết nối vào intersection đó
        return inter.getRoadCount();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // REDRAW
    // ══════════════════════════════════════════════════════════════════════════
    /** Thực sự vẽ lại ngay lập tức (gọi nội bộ từ AnimationTimer). */
    private void redrawNow() {
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
            // Highlight đường đã được chọn (click trong PAN mode)
            if (selectedSegment != null) renderer.drawSelectedHighlight(gc, selectedSegment);

            // DELETE mode: highlight đỏ đoạn đường / nút giao đang hover
            if (currentMode == InteractionType.DELETE) {
                if (hoveredSegment != null) renderer.drawSegmentHighlight(gc, hoveredSegment);
                if (hoveredIntersection != null) renderer.drawIntersectionHighlight(gc, hoveredIntersection);
            }
        }

        // -- Vẽ đèn giao thông (SegmentLights) --
        if (controller != null) {
            for (com.myteam.traffic.light.SegmentLight sl : controller.getAllSegmentLights()) {
                double ang = sl.getAngle(); // góc đường (start → end)
                double baseWx = sl.getWorldX(); // tâm giao lộ (endpoint của segment)
                double baseWy = sl.getWorldY();

                // Tìm bán kính giao lộ để lùi đèn ra stopline
                double interRadius = 30.0;
                if (data != null && data.intersections != null) {
                    for (com.myteam.traffic.model.infrastructure.IntersectionRenderData ird : data.intersections) {
                        if (Math.hypot(baseWx - ird.centerX, baseWy - ird.centerY) < 5.0) {
                            interRadius = ird.radius;
                            break;
                        }
                    }
                }

                // Lùi đèn ra khỏi tâm giao lộ về phía đường đến stopline
                // atEnd=true  → đường đến theo chiều ang → lùi ngược lại (dirSign = -1)
                // atEnd=false → đường đến theo chiều ngược ang → tiến theo ang (dirSign = +1)
                double dirSign = sl.isAtEnd() ? -1.0 : 1.0;
                double stoplineWx = baseWx + dirSign * interRadius * Math.cos(ang);
                double stoplineWy = baseWy + dirSign * interRadius * Math.sin(ang);

                double[] lPos = getLightPosition(sl);
                double wx = lPos[0];
                double wy = lPos[1];
                lightRenderer.draw(gc, sl.getLight(), wx, wy);
                
                // Vẽ hiệu ứng viền cho đèn đang được hover để xoá
                if (currentMode == InteractionType.DELETE_LIGHT && sl == hoveredLight) {
                    double lScale = scale * 0.4;
                    double drawWx = toScreenX(wx);
                    double drawWy = toScreenY(wy);
                    gc.save();
                    gc.translate(drawWx, drawWy);
                    gc.setStroke(Color.web("#ff3333", 0.9));
                    gc.setLineWidth(Math.max(2.0, 3.0 * scale));
                    gc.strokeRoundRect(-25 * lScale, -45 * lScale, 50 * lScale, 90 * lScale, 10 * scale, 10 * scale);
                    gc.setFill(Color.web("#ff3333", 0.45));
                    gc.fillRoundRect(-25 * lScale, -45 * lScale, 50 * lScale, 90 * lScale, 10 * scale, 10 * scale);
                    gc.restore();
                }
            }
        }

        // -- Ve xe (controller.getVehicles()) --
        if (controller != null) {
            gc.save();
            gc.translate(translateX, translateY);
            gc.scale(scale, scale);
            for (Vehicle v : controller.getVehicles()) {
                vehicleRenderer.render(gc, v, isGraphicMode);
                if (deleteVehicleMode && v == hoveredVehicle) {
                    gc.save();
                    gc.translate(v.getX(), v.getY());
                    gc.rotate(v.getDirection().toDegrees());
                    gc.setStroke(Color.RED);
                    gc.setLineWidth(2.0);
                    gc.strokeRect(-v.getWidth() / 2 - 2, -v.getHeight() / 2 - 2, v.getWidth() + 4, v.getHeight() + 4);
                    gc.restore();
                }
            }

            // -- Ve hieu ung no (explosions) --
            for (com.myteam.traffic.ui.ExplosionEffect exp : controller.getExplosions()) {
                double p = exp.progress();
                double expRadius = 5.0 + p * 40.0; // Phóng to dần
                double expAlpha = Math.max(0, 1.0 - p); // Mờ dần

                // Vòng ngoài màu cam
                gc.setFill(Color.web("#ff5500", expAlpha * 0.8));
                gc.fillOval(exp.x - expRadius, exp.y - expRadius, expRadius * 2, expRadius * 2);

                // Vòng trong màu vàng sáng
                double innerRadius = expRadius * 0.6;
                gc.setFill(Color.web("#ffcc00", expAlpha));
                gc.fillOval(exp.x - innerRadius, exp.y - innerRadius, innerRadius * 2, innerRadius * 2);
            }

            gc.restore();
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
            double wx = toWorldX(lastMouseX), wy = toWorldY(lastMouseY);
            // Snap preview vào endpoint gần nhất nếu có
            double[] ep = snapToEndpoint(wx, wy, 50.0 / scale, Double.MAX_VALUE, Double.MAX_VALUE);
            if (ep != null) { wx = ep[0]; wy = ep[1]; }
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

    // ══════════════════════════════════════════════════════════════════════════
    // HOVER HIT-TEST (EDIT_MARKINGS mode)
    // ══════════════════════════════════════════════════════════════════════════

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

        // Chỉ kiểm tra ranh giới trên đường đang được chọn (selectedSegment)
        // Không cho phép sửa vạch của đường khác khi đang ở EDIT_MARKINGS
        Iterable<RoadSegment> candidates = (selectedSegment != null)
                ? java.util.Collections.singletonList(selectedSegment)
                : network.getSegments();

        for (RoadSegment seg : candidates) {
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

    // ══════════════════════════════════════════════════════════════════════════
    // POPUP MENU (EDIT_MARKINGS mode)
    // ══════════════════════════════════════════════════════════════════════════

    // ── Road info popup ──────────────────────────────────────
    // Hover  → info text đi theo chuột, không có nút Sửa
    // Click trái → popup đứng yên, hiện nút ✏ Sửa
    // Mất focus window → tự ẩn
    private final Popup  roadInfoPopup         = new Popup();
    private final Label  roadInfoLabel         = new Label();
    private final Button roadInfoEditBtn       = new Button("✏   Sửa đường này");
    private final Button roadInfoEditMarkBtn   = new Button("🎨  Sửa vạch kẻ");
    private final Button roadInfoLightBtn      = new Button("🚦  Thêm/Sửa đèn");
    private RoadSegment  tipSegment  = null;
    private boolean      tipPinned   = false;   // true = chuột phải đã pin

    {
        roadInfoLabel.setStyle(
                "-fx-font-size:11.5px; -fx-font-family:'Monospaced';" +
                        "-fx-text-fill:#c8d0e8; -fx-padding:0;");
        roadInfoLabel.setWrapText(false);

        // Dòng hướng dẫn — hiện khi hover, ẩn khi pin
        Label hintLabel = new Label("🖱  Chuột phải để ghim  •  Click ngoài để đóng");
        hintLabel.setStyle(
                "-fx-font-size:10px; -fx-text-fill:#4a6080; -fx-padding:2 0 0 0;");

        // Nút Sửa đường này
        roadInfoEditBtn.setStyle(
                "-fx-background-color:#2a5040; -fx-text-fill:#80e8a0;" +
                        "-fx-font-size:11px; -fx-cursor:hand; -fx-padding:5 16 5 16;" +
                        "-fx-background-radius:4; -fx-border-color:#3a8060; -fx-border-radius:4;");
        roadInfoEditBtn.setVisible(false);
        roadInfoEditBtn.setManaged(false);
        roadInfoEditBtn.setOnAction(e -> {
            RoadSegment seg = tipSegment;
            ignoreNextPress = true;
            hideRoadTip();
            if (seg != null && onSegmentSelected != null) {
                selectedSegment = seg;
                onSegmentSelected.accept(seg);
            }
        });

        // Nút Sửa vạch kẻ
        roadInfoEditMarkBtn.setStyle(
                "-fx-background-color:#2a3a60; -fx-text-fill:#80b8ff;" +
                        "-fx-font-size:11px; -fx-cursor:hand; -fx-padding:5 16 5 16;" +
                        "-fx-background-radius:4; -fx-border-color:#3a5898; -fx-border-radius:4;");
        roadInfoEditMarkBtn.setVisible(false);
        roadInfoEditMarkBtn.setManaged(false);
        roadInfoEditMarkBtn.setOnAction(e -> {
            RoadSegment seg = tipSegment;
            ignoreNextPress = true;
            hideRoadTip();
            if (seg != null) {
                selectedSegment = seg;
                previousMode = currentMode; // nhớ mode để restore khi thoát
                setInteractionType(InteractionType.EDIT_MARKINGS);
                if (onMarkingModeEntered != null) onMarkingModeEntered.accept(previousMode);
            }
        });

        // Nút Thêm/Sửa đèn giao thông
        roadInfoLightBtn.setStyle(
                "-fx-background-color:#4a3010; -fx-text-fill:#ffd080;" +
                        "-fx-font-size:11px; -fx-cursor:hand; -fx-padding:5 16 5 16;" +
                        "-fx-background-radius:4; -fx-border-color:#c08030; -fx-border-radius:4;");
        roadInfoLightBtn.setVisible(false);
        roadInfoLightBtn.setManaged(false);
        roadInfoLightBtn.setOnAction(e -> {
            RoadSegment seg = tipSegment;
            ignoreNextPress = true;
            hideRoadTip();
            if (seg != null && controller != null) {
                showTrafficLightDialog(seg);
            }
        });

        // HBox chứa 3 nút cạnh nhau — hàng 1: Sửa đường + Sửa vạch, hàng 2: Thêm đèn
        javafx.scene.layout.HBox btnRow1 = new javafx.scene.layout.HBox(8,
                roadInfoEditBtn, roadInfoEditMarkBtn);
        javafx.scene.layout.HBox btnRow2 = new javafx.scene.layout.HBox(8,
                roadInfoLightBtn);

        // hintLabel là index 1, btnRow1 là index 2, btnRow2 là index 3
        VBox box = new VBox(6, roadInfoLabel, hintLabel, btnRow1, btnRow2);
        box.setStyle(
                "-fx-background-color:#0d1420e8; -fx-border-color:#3a4a6a;" +
                        "-fx-border-width:1; -fx-padding:8 12 8 12;" +
                        "-fx-background-radius:6; -fx-border-radius:6;");

        roadInfoPopup.getContent().add(box);
        roadInfoPopup.setAutoHide(false);
        roadInfoPopup.setConsumeAutoHidingEvents(false);
    }

    /** Dựng lại nội dung label từ segment. */
    private String buildRoadTipText(RoadSegment seg) {
        StringBuilder sb = new StringBuilder();
        if (seg instanceof HighwaySegment hwy) {
            sb.append("🛣️  CAO TỐC | ").append(seg.getLaneCount()).append(" làn");
            sb.append("  ≥").append((int)hwy.getMinSpeedLimit()).append(" km/h");
            if (hwy.hasEmergencyLane()) sb.append("  🟠Làn KC");
        } else if (seg instanceof HighwayRampSegment ramp) {
            sb.append(ramp.getRampType() == HighwayRampSegment.RampType.ONRAMP
                    ? "⬇️  VÀO CAO TỐC" : "⬆️  RA CAO TỐC");
            sb.append(" | ").append(seg.getLaneCount()).append(" làn");
        } else {
            sb.append("🛣️  Đường thường | ").append(seg.getLaneCount()).append(" làn");
        }
        sb.append(String.format("  |  %.0f m\n", seg.getLength() / 20.0 * 3.5));
        for (Lane l : seg.getLanes()) {
            sb.append(l.getDirection() == Lane.Direction.FORWARD ? "  →" : "  ←");
            sb.append(String.format(" %.1fm", l.getWidth()));
            if (l.getAllowedVehicles().contains(Lane.VehicleCategory.CAR))       sb.append(" 🚗");
            if (l.getAllowedVehicles().contains(Lane.VehicleCategory.MOTORBIKE)) sb.append(" 🛵");
            if (l.getAllowedVehicles().contains(Lane.VehicleCategory.BUS))       sb.append(" 🚌");
            if (l.getAllowedVehicles().contains(Lane.VehicleCategory.TRUCK))     sb.append(" 🚛");
            if (l.getAllowedVehicles().contains(Lane.VehicleCategory.BICYCLE))   sb.append(" 🚲");
            if (l.getAllowedVehicles().contains(Lane.VehicleCategory.EMERGENCY)) sb.append(" 🚨");
            sb.append("  [");
            if (l.getAllowedMovements().contains(Lane.Movement.STRAIGHT)) sb.append("↑");
            if (l.getAllowedMovements().contains(Lane.Movement.LEFT))     sb.append("↰");
            if (l.getAllowedMovements().contains(Lane.Movement.RIGHT))    sb.append("↱");
            if (l.getAllowedMovements().contains(Lane.Movement.U_TURN))  sb.append("↩");
            sb.append("]\n");
        }
        return sb.toString().trim();
    }

    private void showRoadTip(RoadSegment seg, double screenX, double screenY) {
        if (tipPinned) return;
        if (seg != tipSegment) {
            tipSegment = seg;
            roadInfoLabel.setText(buildRoadTipText(seg));
        }
        // Hover: hint visible, hàng nút ẩn
        VBox box = (VBox) roadInfoPopup.getContent().get(0);
        box.getChildren().get(1).setVisible(true);   // hintLabel
        box.getChildren().get(1).setManaged(true);
        box.getChildren().get(2).setVisible(false);  // btnRow1
        box.getChildren().get(2).setManaged(false);
        box.getChildren().get(3).setVisible(false);  // btnRow2
        box.getChildren().get(3).setManaged(false);
        roadInfoEditBtn.setVisible(false);
        roadInfoEditBtn.setManaged(false);
        roadInfoEditMarkBtn.setVisible(false);
        roadInfoEditMarkBtn.setManaged(false);
        roadInfoLightBtn.setVisible(false);
        roadInfoLightBtn.setManaged(false);
        if (!roadInfoPopup.isShowing())
            roadInfoPopup.show(this, screenX + 18, screenY + 14);
        else {
            roadInfoPopup.setAnchorX(screenX + 18);
            roadInfoPopup.setAnchorY(screenY + 14);
        }
    }

    private void pinRoadTip(RoadSegment seg, double screenX, double screenY) {
        tipPinned = true;
        tipSegment = seg;
        roadInfoLabel.setText(buildRoadTipText(seg));
        // Pin: ẩn hint, hiện hàng nút (cả 3 nút Sửa)
        VBox box = (VBox) roadInfoPopup.getContent().get(0);
        box.getChildren().get(1).setVisible(false);  // hintLabel
        box.getChildren().get(1).setManaged(false);
        box.getChildren().get(2).setVisible(true);   // btnRow1
        box.getChildren().get(2).setManaged(true);
        box.getChildren().get(3).setVisible(true);   // btnRow2 (light)
        box.getChildren().get(3).setManaged(true);
        roadInfoEditBtn.setVisible(true);
        roadInfoEditBtn.setManaged(true);
        roadInfoEditMarkBtn.setVisible(true);
        roadInfoEditMarkBtn.setManaged(true);
        roadInfoLightBtn.setVisible(true);
        roadInfoLightBtn.setManaged(true);
        // Luôn show tại vị trí mới — kể cả đang hiện từ hover trước đó
        roadInfoPopup.hide();
        roadInfoPopup.show(this, screenX + 18, screenY + 14);
    }

    private void hideRoadTip() {
        tipSegment = null;
        tipPinned  = false;
        roadInfoEditBtn.setVisible(false);
        roadInfoEditBtn.setManaged(false);
        roadInfoEditMarkBtn.setVisible(false);
        roadInfoEditMarkBtn.setManaged(false);
        roadInfoLightBtn.setVisible(false);
        roadInfoLightBtn.setManaged(false);
        // Ẩn btnRow1 + btnRow2
        VBox box = (VBox) roadInfoPopup.getContent().get(0);
        if (box.getChildren().size() > 2) {
            box.getChildren().get(2).setVisible(false);
            box.getChildren().get(2).setManaged(false);
        }
        if (box.getChildren().size() > 3) {
            box.getChildren().get(3).setVisible(false);
            box.getChildren().get(3).setManaged(false);
        }
        roadInfoPopup.hide();
    }

    /**
     * Hiển thị dialog thêm/sửa đèn giao thông cho đoạn đường.
     * Cho phép cấu hình thời gian xanh/đỏ/vàng, và chọn đặt đèn ở đầu hay cuối đường.
     */
    private void showTrafficLightDialog(RoadSegment seg) {
        javafx.stage.Stage dlg = new javafx.stage.Stage();
        dlg.setTitle("🚦 Cấu hình đèn giao thông — đoạn đường");
        dlg.initModality(javafx.stage.Modality.APPLICATION_MODAL);

        String DARK = "-fx-background-color:#1a2030;";
        String FIELD_STYLE = "-fx-background-color:#2a3550; -fx-text-fill:#e8d44d; " +
                "-fx-font-size:12px; -fx-border-color:#3a4560; -fx-border-radius:4; -fx-background-radius:4;";

        javafx.scene.layout.VBox root = new javafx.scene.layout.VBox(14);
        root.setStyle(DARK + " -fx-padding:18;");

        // Tiêu đề
        javafx.scene.control.Label title = new javafx.scene.control.Label("🚦  Đèn giao thông — " +
                seg.getLaneCount() + " làn  |  " + String.format("%.0f m", seg.getLength() / 20.0 * 3.5));
        title.setStyle("-fx-text-fill:#ffd080; -fx-font-size:13px; -fx-font-weight:bold;");
        root.getChildren().add(title);

        // Tìm đèn hiện tại gắn với segment này (nếu có)
        java.util.List<com.myteam.traffic.light.SegmentLight> existing =
                controller.getSegmentLightsForSegment(seg);
        com.myteam.traffic.light.SegmentLight existingLight =
                existing.isEmpty() ? null : existing.get(0);

        // Spinner thời gian
        javafx.scene.control.Label lblGreen  = new javafx.scene.control.Label("🟢  Xanh (giây):");
        javafx.scene.control.Label lblRed    = new javafx.scene.control.Label("🔴  Đỏ (giây):");
        javafx.scene.control.Label lblYellow = new javafx.scene.control.Label("🟡  Vàng (giây):");
        for (javafx.scene.control.Label l : new javafx.scene.control.Label[]{lblGreen, lblRed, lblYellow})
            l.setStyle("-fx-text-fill:#c8d0e8; -fx-font-size:12px;");

        int initGreen  = 30;
        int initRed    = 30;
        int initYellow = 5;
        // Lấy thời gian hiện tại nếu có đèn sẵn
        if (existingLight != null) {
            com.myteam.traffic.light.TrafficLight tl = existingLight.getLight();
            // Dùng getDurationForState thông qua reflection hoặc mặc định 30/5/30
            initGreen  = tl.getDurationForState(com.myteam.traffic.light.TrafficLightState.GREEN);
            initRed    = tl.getDurationForState(com.myteam.traffic.light.TrafficLightState.RED);
            initYellow = tl.getDurationForState(com.myteam.traffic.light.TrafficLightState.YELLOW);
        }

        javafx.scene.control.Spinner<Integer> spinGreen  = new javafx.scene.control.Spinner<>(1, 999, Math.min(999, Math.max(1, initGreen)));
        javafx.scene.control.Spinner<Integer> spinRed    = new javafx.scene.control.Spinner<>(1, 999, Math.min(999, Math.max(1, initRed)));
        javafx.scene.control.Spinner<Integer> spinYellow = new javafx.scene.control.Spinner<>(1, 999, Math.min(999, Math.max(1, initYellow)));
        for (javafx.scene.control.Spinner<Integer> s : new javafx.scene.control.Spinner[]{spinGreen, spinRed, spinYellow}) {
            s.setEditable(true);
            s.setPrefWidth(90);
            s.setStyle("-fx-background-color:#2a3550; -fx-border-color:#3a4560;");
            s.getEditor().setStyle(FIELD_STYLE);
        }

        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(12); grid.setVgap(10);
        grid.add(lblGreen,  0, 0); grid.add(spinGreen,  1, 0);
        grid.add(lblRed,    0, 1); grid.add(spinRed,    1, 1);
        grid.add(lblYellow, 0, 2); grid.add(spinYellow, 1, 2);

        javafx.scene.control.Label lblInitState = new javafx.scene.control.Label("Trạng thái hiện tại:");
        lblInitState.setStyle("-fx-text-fill:#c8d0e8; -fx-font-size:12px;");
        javafx.scene.control.ComboBox<com.myteam.traffic.light.TrafficLightState> cmbInitState = new javafx.scene.control.ComboBox<>();
        cmbInitState.getItems().addAll(com.myteam.traffic.light.TrafficLightState.GREEN, com.myteam.traffic.light.TrafficLightState.RED, com.myteam.traffic.light.TrafficLightState.YELLOW);
        cmbInitState.setValue(existingLight != null ? existingLight.getLight().getCurrentState() : com.myteam.traffic.light.TrafficLightState.GREEN);
        cmbInitState.setStyle("-fx-background-color:#2a3550; -fx-text-fill:white; -fx-font-size:11px;");

        javafx.scene.control.Label lblInitSec = new javafx.scene.control.Label("Số giây hiện tại:");
        lblInitSec.setStyle("-fx-text-fill:#c8d0e8; -fx-font-size:12px;");
        int initSecVal = existingLight != null ? Math.max(1, (int)Math.ceil(existingLight.getLight().getSecondsRemaining())) : 15;
        initSecVal = Math.min(999, initSecVal);
        javafx.scene.control.Spinner<Integer> spinInitSec = new javafx.scene.control.Spinner<>(1, 999, initSecVal);
        spinInitSec.setEditable(true);
        spinInitSec.setPrefWidth(90);
        spinInitSec.setStyle("-fx-background-color:#2a3550; -fx-border-color:#3a4560;");
        spinInitSec.getEditor().setStyle(FIELD_STYLE);

        grid.add(lblInitState, 0, 3); grid.add(cmbInitState, 1, 3);
        grid.add(lblInitSec, 0, 4); grid.add(spinInitSec, 1, 4);

        root.getChildren().add(grid);

        // Chọn vị trí đèn: đầu hay cuối đường
        javafx.scene.control.Label lblPos = new javafx.scene.control.Label("Vị trí đèn:");
        lblPos.setStyle("-fx-text-fill:#c8d0e8; -fx-font-size:12px;");
        javafx.scene.control.ToggleButton btnEnd   = new javafx.scene.control.ToggleButton("Ngã tư Cuối đường →");
        javafx.scene.control.ToggleButton btnStart = new javafx.scene.control.ToggleButton("← Ngã tư Đầu đường");
        javafx.scene.control.ToggleGroup posGroup  = new javafx.scene.control.ToggleGroup();
        btnEnd.setToggleGroup(posGroup);
        btnStart.setToggleGroup(posGroup);
        String toggleBase = "-fx-font-size:11px; -fx-cursor:hand; -fx-padding:4 10 4 10; -fx-background-radius:4;";
        btnEnd  .setStyle(toggleBase + "-fx-background-color:#2a3550; -fx-text-fill:#c8d0e8;");
        btnStart.setStyle(toggleBase + "-fx-background-color:#2a3550; -fx-text-fill:#c8d0e8;");
        // Highlight khi chọn
        for (javafx.scene.control.ToggleButton tb : new javafx.scene.control.ToggleButton[]{btnEnd, btnStart}) {
            tb.selectedProperty().addListener((o, was, is) ->
                tb.setStyle(toggleBase + (is
                    ? "-fx-background-color:#3a6090; -fx-text-fill:white;"
                    : "-fx-background-color:#2a3550; -fx-text-fill:#c8d0e8;"))
            );
        }
        
        Intersection interStart = network.findNearestIntersection(seg.getStartX(), seg.getStartY(), 5.0);
        Intersection interEnd = network.findNearestIntersection(seg.getEndX(), seg.getEndY(), 5.0);
        
        boolean existingAtEnd = existingLight == null || existingLight.isAtEnd();
        
        if (interStart == null && interEnd != null) {
            btnEnd.setSelected(true);
            btnStart.setDisable(true);
        } else if (interStart != null && interEnd == null) {
            btnStart.setSelected(true);
            btnEnd.setDisable(true);
        } else {
            if (existingAtEnd) btnEnd.setSelected(true); else btnStart.setSelected(true);
        }
        
        javafx.scene.layout.HBox posRow = new javafx.scene.layout.HBox(8, lblPos, btnEnd, btnStart);
        posRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        root.getChildren().add(posRow);

        // Hiển thị trạng thái đèn hiện tại nếu đang edit
        if (existingLight != null) {
            javafx.scene.control.Label liveStateLabel = new javafx.scene.control.Label();
            liveStateLabel.setStyle("-fx-font-size:12px; -fx-font-weight:bold;");
            Runnable updateLabel = () -> {
                com.myteam.traffic.light.TrafficLight tl = existingLight.getLight();
                com.myteam.traffic.light.TrafficLightState st = tl.getCurrentState();
                int remain = (int) Math.ceil(tl.getSecondsRemaining());
                String color = switch(st) {
                    case GREEN -> "#40ff40";
                    case RED -> "#ff4040";
                    case YELLOW -> "#ffff40";
                };
                liveStateLabel.setText(String.format("Trạng thái: %s · Còn: %ds", st, remain));
                liveStateLabel.setTextFill(javafx.scene.paint.Color.web(color));
            };
            updateLabel.run();
            // Timeline để update nhãn mỗi 0.5s
            javafx.animation.Timeline timeline = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(javafx.util.Duration.seconds(0.5), e -> updateLabel.run())
            );
            timeline.setCycleCount(javafx.animation.Animation.INDEFINITE);
            timeline.play();
            dlg.setOnHidden(e -> timeline.stop());
            root.getChildren().add(liveStateLabel);
        }

        // Chọn loại đèn (Không đếm ngược, Đếm 10s cuối)
        javafx.scene.control.CheckBox chkNoCountdown = new javafx.scene.control.CheckBox("Không đếm ngược");
        chkNoCountdown.setStyle("-fx-text-fill:#c8d0e8; -fx-font-size:12px;");
        javafx.scene.control.CheckBox chkTenSec = new javafx.scene.control.CheckBox("Đếm 10s cuối");
        chkTenSec.setStyle("-fx-text-fill:#c8d0e8; -fx-font-size:12px;");

        // Loại trừ nhau
        chkNoCountdown.selectedProperty().addListener((o, oldV, newV) -> {
            if (newV) chkTenSec.setSelected(false);
        });
        chkTenSec.selectedProperty().addListener((o, oldV, newV) -> {
            if (newV) chkNoCountdown.setSelected(false);
        });

        if (existingLight != null) {
            com.myteam.traffic.light.TrafficLight tl = existingLight.getLight();
            if (tl instanceof com.myteam.traffic.light.NoCountdownLight) {
                chkNoCountdown.setSelected(true);
            } else if (tl instanceof com.myteam.traffic.light.TenSecLight) {
                chkTenSec.setSelected(true);
            }
        }

        javafx.scene.layout.HBox typeRow = new javafx.scene.layout.HBox(15, chkNoCountdown, chkTenSec);
        root.getChildren().add(typeRow);

        // Ghi chú
        javafx.scene.control.Label note = new javafx.scene.control.Label(
                "💡 Đèn sẽ áp dụng đồng bộ cho toàn bộ nút giao.");
        note.setStyle("-fx-text-fill:#5a7090; -fx-font-size:11px;");
        root.getChildren().add(note);

        // Buttons
        javafx.scene.control.Button btnApply  = new javafx.scene.control.Button("✔ Áp dụng");
        javafx.scene.control.Button btnDelete = new javafx.scene.control.Button("🗑 Xóa đèn");
        javafx.scene.control.Button btnCancel = new javafx.scene.control.Button("Hủy");

        btnApply.setStyle("-fx-background-color:#2a5a2a; -fx-text-fill:white; -fx-font-weight:bold; " +
                "-fx-cursor:hand; -fx-padding:5 16 5 16; -fx-background-radius:5;");
        btnDelete.setStyle("-fx-background-color:#5a1a1a; -fx-text-fill:#ff9090; -fx-font-weight:bold; " +
                "-fx-cursor:hand; -fx-padding:5 16 5 16; -fx-background-radius:5;");
        btnCancel.setStyle("-fx-background-color:#3a3030; -fx-text-fill:#c8d0e8; " +
                "-fx-cursor:hand; -fx-padding:5 16 5 16; -fx-background-radius:5;");

        if (existingLight == null) btnDelete.setDisable(true);

        btnApply.setOnAction(ev -> {
            int g = spinGreen.getValue();
            int r = spinRed.getValue();
            int y = spinYellow.getValue();
            boolean mainAtEnd = btnEnd.isSelected();

            double interX = mainAtEnd ? seg.getEndX() : seg.getStartX();
            double interY = mainAtEnd ? seg.getEndY() : seg.getStartY();
            
            // Backup trạng thái cũ để undo
            List<com.myteam.traffic.light.SegmentLight> backupLights = new java.util.ArrayList<>(controller.getAllSegmentLights());
            
            // Xóa tất cả đèn cũ ở nút giao này để cập nhật lại đồng bộ
            for (com.myteam.traffic.model.infrastructure.RoadSegment s : data.roads) {
                if (Math.hypot(s.getEndX() - interX, s.getEndY() - interY) < 1.0) controller.removeSegmentLight(s, true);
                if (Math.hypot(s.getStartX() - interX, s.getStartY() - interY) < 1.0) controller.removeSegmentLight(s, false);
            }

            double mainAngle = mainAtEnd ? 
                Math.atan2(seg.getEndY() - seg.getStartY(), seg.getEndX() - seg.getStartX()) :
                Math.atan2(seg.getStartY() - seg.getEndY(), seg.getStartX() - seg.getEndX());

            com.myteam.traffic.light.TrafficLightState mainInitialState = cmbInitState.getValue();
            int mainInitialSec = spinInitSec.getValue();

            // FIX: Để ngã tư không bao giờ bị lệch pha, thời gian ĐỎ bắt buộc phải bằng XANH + VÀNG
            int syncR = g + y; 
            
            // Xử lý logic khởi tạo state cho nhánh đối lập
            com.myteam.traffic.light.TrafficLightState oppState;
            int oppSec;
            if (mainInitialState == com.myteam.traffic.light.TrafficLightState.GREEN) {
                oppState = com.myteam.traffic.light.TrafficLightState.RED;
                oppSec = mainInitialSec + y; // Phải đợi hết xanh và vàng của nhánh chính
            } else if (mainInitialState == com.myteam.traffic.light.TrafficLightState.YELLOW) {
                oppState = com.myteam.traffic.light.TrafficLightState.RED;
                oppSec = mainInitialSec;     // Chỉ đợi hết vàng của nhánh chính
            } else { // Nhánh chính đang ĐỎ
                if (mainInitialSec > y) {
                    oppState = com.myteam.traffic.light.TrafficLightState.GREEN;
                    oppSec = mainInitialSec - y;
                } else {
                    oppState = com.myteam.traffic.light.TrafficLightState.YELLOW;
                    oppSec = mainInitialSec;
                }
            }

            for (com.myteam.traffic.model.infrastructure.RoadSegment s : data.roads) {
                boolean sAtEnd = false;
                boolean connected = false;
                if (Math.hypot(s.getEndX() - interX, s.getEndY() - interY) < 1.0) { sAtEnd = true; connected = true; }
                else if (Math.hypot(s.getStartX() - interX, s.getStartY() - interY) < 1.0) { sAtEnd = false; connected = true; }

                if (connected) {
                    double sAngle = sAtEnd ? 
                        Math.atan2(s.getEndY() - s.getStartY(), s.getEndX() - s.getStartX()) :
                        Math.atan2(s.getStartY() - s.getEndY(), s.getStartX() - s.getEndX());

                    double diff = Math.abs(mainAngle - sAngle);
                    while (diff > Math.PI) diff -= 2 * Math.PI;
                    diff = Math.abs(diff);

                    com.myteam.traffic.light.TrafficLight newLight;
                    if (chkNoCountdown.isSelected()) {
                        newLight = new com.myteam.traffic.light.NoCountdownLight(syncR, g, y);
                    } else if (chkTenSec.isSelected()) {
                        newLight = new com.myteam.traffic.light.TenSecLight(syncR, g, y);
                    } else {
                        newLight = new com.myteam.traffic.light.CountdownLight(syncR, g, y);
                    }
                    
                    if (diff <= Math.PI / 4 || Math.abs(diff - Math.PI) <= Math.PI / 4) {
                        newLight.setInitialState(mainInitialState, mainInitialSec);
                    } else {
                        newLight.setInitialState(oppState, oppSec);
                    }
                    controller.addSegmentLight(new com.myteam.traffic.light.SegmentLight(s, newLight, sAtEnd));
                }
            }
            
            // Push undo action
            undoStack.push(() -> {
                for (com.myteam.traffic.model.infrastructure.RoadSegment s : data.roads) {
                    if (Math.hypot(s.getEndX() - interX, s.getEndY() - interY) < 1.0) controller.removeSegmentLight(s, true);
                    if (Math.hypot(s.getStartX() - interX, s.getStartY() - interY) < 1.0) controller.removeSegmentLight(s, false);
                }
                for (com.myteam.traffic.light.SegmentLight sl : backupLights) {
                    controller.addSegmentLight(sl);
                }
            });

            redraw();
            dlg.close();
        });

        btnDelete.setOnAction(ev -> {
            boolean mainAtEnd = btnEnd.isSelected();
            List<com.myteam.traffic.light.SegmentLight> backupLights = new java.util.ArrayList<>(controller.getAllSegmentLights());
            
            controller.removeSegmentLight(seg, mainAtEnd);
            
            undoStack.push(() -> {
                controller.removeSegmentLight(seg, mainAtEnd);
                for (com.myteam.traffic.light.SegmentLight sl : backupLights) {
                    controller.addSegmentLight(sl);
                }
            });
            
            redraw();
            dlg.close();
        });

        btnCancel.setOnAction(ev -> dlg.close());

        javafx.scene.layout.HBox btnRow = new javafx.scene.layout.HBox(10, btnApply, btnDelete, btnCancel);
        btnRow.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        root.getChildren().add(btnRow);

        javafx.scene.Scene scene = new javafx.scene.Scene(root, 380, 420);
        scene.getStylesheets().add("data:text/css," +
                ".spinner .increment-arrow-button{-fx-background-color:#3a4560;}" +
                ".spinner .decrement-arrow-button{-fx-background-color:#3a4560;}" +
                ".spinner .increment-arrow-button .increment-arrow{-fx-background-color:#c8d0e8;}" +
                ".spinner .decrement-arrow-button .decrement-arrow{-fx-background-color:#c8d0e8;}" +
                ".combo-box .list-cell {-fx-text-fill: white;}" +
                ".combo-box-popup .list-view {-fx-background-color: #2a3550;}" +
                ".combo-box-popup .list-cell {-fx-text-fill: white;}" +
                ".combo-box-popup .list-cell:filled:selected, .combo-box-popup .list-cell:filled:hover {-fx-background-color: #3a4560;}");
        dlg.setScene(scene);
        dlg.show();
    }

    // ── Marking picker popup ───────────────────────────────────
    private Popup activeMarkingPopup = null;
    private ContextMenu activeMenu = null;

    private void hideActiveMenu() {
        if (activeMenu != null) { activeMenu.hide(); activeMenu = null; }
        if (activeMarkingPopup != null) { activeMarkingPopup.hide(); activeMarkingPopup = null; }
    }

    private void showMarkingMenu(HoverResult target, double screenX, double screenY) {
        hideActiveMenu();

        Lane currentLane = target.seg.getLanes().get(target.boundaryIndex - 1);
        Lane.MarkingType current = currentLane.getRightMarking();

        // Xác định màu hiện tại (vàng hay trắng)
        boolean isYellow = (current == Lane.MarkingType.YELLOW_SOLID
                || current == Lane.MarkingType.YELLOW_DASHED
                || current == Lane.MarkingType.YELLOW_DOUBLE_SOLID
                || current == Lane.MarkingType.YELLOW_LEFT_DASHED_RIGHT_SOLID
                || current == Lane.MarkingType.YELLOW_LEFT_SOLID_RIGHT_DASHED);

        // ── Danh sách kiểu vạch (style → white variant, yellow variant) ──
        record MarkEntry(String label, Lane.MarkingType white, Lane.MarkingType yellow) {}
        List<MarkEntry> entries = List.of(
                new MarkEntry("Nét đứt",               Lane.MarkingType.DASHED,                     Lane.MarkingType.YELLOW_DASHED),
                new MarkEntry("Nét liền",              Lane.MarkingType.SOLID,                      Lane.MarkingType.YELLOW_SOLID),
                new MarkEntry("Nét đôi",               Lane.MarkingType.DOUBLE_SOLID,               Lane.MarkingType.YELLOW_DOUBLE_SOLID),
                new MarkEntry("Trái đứt / Phải liền",  Lane.MarkingType.LEFT_DASHED_RIGHT_SOLID,    Lane.MarkingType.YELLOW_LEFT_DASHED_RIGHT_SOLID),
                new MarkEntry("Trái liền / Phải đứt",  Lane.MarkingType.LEFT_SOLID_RIGHT_DASHED,    Lane.MarkingType.YELLOW_LEFT_SOLID_RIGHT_DASHED),
                new MarkEntry("Xóa vạch",              Lane.MarkingType.NONE,                       Lane.MarkingType.NONE)
        );

        // Xác định entry hiện tại đang chọn
        MarkEntry currentEntry = entries.stream()
                .filter(en -> en.white() == current || en.yellow() == current)
                .findFirst().orElse(entries.get(0));

        // ── Toggle màu ───────────────────────────────────────────
        javafx.beans.property.BooleanProperty yellowMode =
                new javafx.beans.property.SimpleBooleanProperty(isYellow);

        // ── Build popup content ──────────────────────────────────
        javafx.scene.layout.VBox root = new javafx.scene.layout.VBox(0);
        root.setStyle(
                "-fx-background-color:#16202e; -fx-border-color:#3a4a6a;" +
                        "-fx-border-width:1; -fx-background-radius:6; -fx-border-radius:6;");

        // Header: chọn màu trắng / vàng
        javafx.scene.layout.HBox colorRow = new javafx.scene.layout.HBox(0);
        colorRow.setStyle("-fx-padding:6 8 6 8; -fx-border-color:transparent transparent #2a3a55 transparent; -fx-border-width:0 0 1 0;");
        Button btnWhite  = new Button("⬜ Trắng");
        Button btnYellow = new Button("🟡 Vàng");
        String baseBtn   = "-fx-font-size:11px; -fx-cursor:hand; -fx-padding:3 12 3 12;" +
                "-fx-background-radius:3; -fx-border-radius:3; -fx-border-width:1;";
        Runnable refreshColors = () -> {
            boolean y = yellowMode.get();
            btnWhite .setStyle(baseBtn + (!y
                    ? "-fx-background-color:#2a4060; -fx-text-fill:#e8f0ff; -fx-border-color:#5080c0;"
                    : "-fx-background-color:#1a2535; -fx-text-fill:#6a80a0; -fx-border-color:#2a3a55;"));
            btnYellow.setStyle(baseBtn + ( y
                    ? "-fx-background-color:#4a3800; -fx-text-fill:#ffe060; -fx-border-color:#c0a020;"
                    : "-fx-background-color:#1a2535; -fx-text-fill:#6a80a0; -fx-border-color:#2a3a55;"));
        };
        refreshColors.run();
        btnWhite .setOnAction(ev -> { yellowMode.set(false); refreshColors.run(); });
        btnYellow.setOnAction(ev -> { yellowMode.set(true);  refreshColors.run(); });
        colorRow.getChildren().addAll(btnWhite, new javafx.scene.layout.Region() {{
            javafx.scene.layout.HBox.setHgrow(this, javafx.scene.layout.Priority.ALWAYS);
        }}, btnYellow);

        root.getChildren().add(colorRow);

        // Danh sách vạch
        for (MarkEntry entry : entries) {
            boolean isCurrent = (entry == currentEntry);
            Button btn = new Button((isCurrent ? "✔  " : "     ") + entry.label());
            btn.setMaxWidth(Double.MAX_VALUE);
            btn.setStyle(
                    "-fx-background-color:" + (isCurrent ? "#1e3a5a" : "transparent") + ";" +
                            "-fx-text-fill:" + (isCurrent ? "#90c8ff" : "#c8d0e8") + ";" +
                            "-fx-font-size:12.5px; -fx-cursor:hand; -fx-padding:7 14 7 14;" +
                            "-fx-alignment:CENTER_LEFT; -fx-border-width:0; -fx-background-radius:0;");
            btn.setOnMouseEntered(ev -> {
                if (!isCurrent) btn.setStyle(btn.getStyle().replace("transparent", "#1a2a3e"));
            });
            btn.setOnMouseExited(ev -> {
                if (!isCurrent) btn.setStyle(btn.getStyle().replace("#1a2a3e", "transparent"));
            });
            btn.setOnAction(ev -> {
                Lane.MarkingType chosen = yellowMode.get() ? entry.yellow() : entry.white();
                applyMarking(target, chosen);
                hideActiveMenu();
            });
            root.getChildren().add(btn);
        }

        Popup popup = new Popup();
        popup.setAutoHide(true);
        popup.getContent().add(root);
        popup.show(this, screenX, screenY);
        activeMarkingPopup = popup;
        popup.setOnHidden(ev -> { if (activeMarkingPopup == popup) activeMarkingPopup = null; });
    }

    private void applyMarking(HoverResult target, Lane.MarkingType type) {
        saveSnapshot();
        List<Lane> lanes    = target.seg.getLanes();
        List<Lane> newLanes = new ArrayList<>(lanes);
        Lane leftLane  = lanes.get(target.boundaryIndex - 1);
        Lane rightLane = lanes.get(target.boundaryIndex);
        newLanes.set(target.boundaryIndex - 1, leftLane.withRightMarking(type));
        newLanes.set(target.boundaryIndex,     rightLane.withLeftMarking(type));
        RoadSegment newSeg = target.seg.withNewLanes(newLanes);
        network.replaceSegment(target.seg, newSeg);
        // Cập nhật selectedSegment sang object mới — tránh stale reference
        // (Lane là immutable nên replaceSegment tạo object hoàn toàn mới)
        if (selectedSegment == target.seg) selectedSegment = newSeg;
        hoveredBoundary = null;
        updateRenderData();
        hoveredBoundary = hitTestBoundary(
                toWorldX(target.screenX), toWorldY(target.screenY));
        redraw();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // INTERSECTION HIT-TEST HELPERS
    // ══════════════════════════════════════════════════════════════════════════
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

    // ══════════════════════════════════════════════════════════════════════════
    // MOUSE EVENTS
    // ══════════════════════════════════════════════════════════════════════════
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
            
            if (deleteVehicleMode && controller != null) {
                Vehicle best = null;
                double bestDist = 15.0; // 15 world units hover radius
                for (Vehicle v : controller.getVehicles()) {
                    double dist = Math.hypot(v.getX() - wx, v.getY() - wy);
                    if (dist < bestDist) {
                        bestDist = dist;
                        best = v;
                    }
                }
                if (hoveredVehicle != best) {
                    hoveredVehicle = best;
                    redraw();
                }
            }

            if (currentMode == InteractionType.EDIT_MARKINGS) {
                HoverResult hit = hitTestBoundary(wx, wy);
                if (hit != hoveredBoundary) { hoveredBoundary = hit; redraw(); }
                if (!tipPinned) hideRoadTip();
            } else if (currentMode == InteractionType.PLACE_INTERSECTION) {
                lastMouseX = e.getX(); lastMouseY = e.getY(); redraw();
                if (!tipPinned) hideRoadTip();
            } else if (currentMode == InteractionType.DELETE) {
                if (network == null) return;
                RoadSegment  newSeg   = hitTestSegment(wx, wy);
                Intersection newInter = (newSeg == null)
                        ? network.findNearestIntersection(wx, wy, 30.0 / scale) : null;
                if (newSeg != hoveredSegment || newInter != hoveredIntersection) {
                    hoveredSegment = newSeg; hoveredIntersection = newInter; redraw();
                }
                // Popup dùng vùng rộng hơn
                RoadSegment popupSeg = hitTestSegmentForPopup(wx, wy);
                if (popupSeg != null) showRoadTip(popupSeg, e.getScreenX(), e.getScreenY());
                else if (!tipPinned) hideRoadTip();
            } else if (currentMode == InteractionType.DELETE_LIGHT) {
                com.myteam.traffic.light.SegmentLight newHoveredLight = hitTestLight(wx, wy);
                if (newHoveredLight != hoveredLight) {
                    hoveredLight = newHoveredLight;
                    redraw();
                }
                if (!tipPinned) hideRoadTip();
            } else {
                // PAN / DRAW mode: popup đi theo chuột khi hover trên đường
                RoadSegment hovered = hitTestSegmentForPopup(wx, wy);
                if (hovered != null)
                    showRoadTip(hovered, e.getScreenX(), e.getScreenY());
                else if (!tipPinned)
                    hideRoadTip();
            }
        });

        // MOUSE_PRESSED
        addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            // Bỏ qua press đầu tiên sau khi popup đóng (tránh pan/select không mong muốn)
            if (ignoreNextPress) { ignoreNextPress = false; e.consume(); return; }
            if (e.getButton() == MouseButton.SECONDARY) {
                if (currentMode == InteractionType.EDIT_MARKINGS && hoveredBoundary != null) {
                    showMarkingMenu(hoveredBoundary, e.getScreenX(), e.getScreenY());
                } else if (currentMode == InteractionType.EDIT_MARKINGS) {
                    // Chuột phải ra ngoài đường trong EDIT_MARKINGS → thoát, restore mode cũ
                    double wxE = toWorldX(e.getX()), wyE = toWorldY(e.getY());
                    if (hitTestSegmentForPopup(wxE, wyE) == null) {
                        selectedSegment = null;
                        setInteractionType(previousMode);
                        if (onMarkingModeEntered != null) onMarkingModeEntered.accept(null);
                    }
                    hideActiveMenu();
                    lastMouseX = e.getX(); lastMouseY = e.getY();
                } else {
                    // Chuột phải trong BẤT KỲ mode nào: nếu trỏ vào đường → pin popup + nút Sửa
                    double wxR = toWorldX(e.getX()), wyR = toWorldY(e.getY());
                    RoadSegment rightSeg = hitTestSegmentForPopup(wxR, wyR);
                    if (rightSeg != null) {
                        pinRoadTip(rightSeg, e.getScreenX(), e.getScreenY());
                    } else {
                        // Click chuột phải ra ngoài đường → đóng popup nếu đang pin
                        if (tipPinned) hideRoadTip();
                        hideActiveMenu();
                        lastMouseX = e.getX(); lastMouseY = e.getY();
                    }
                }
                return;
            }
            if (e.getButton() != MouseButton.PRIMARY) return;
            hideActiveMenu();

            if (deleteVehicleMode && hoveredVehicle != null) {
                if (controller != null) {
                    controller.removeVehicle(hoveredVehicle);
                }
                hoveredVehicle = null;
                redraw();
                return;
            }

            // Click trái ra ngoài đường → ẩn popup nếu đang pin
            double wx0 = toWorldX(e.getX()), wy0 = toWorldY(e.getY());
            if (tipPinned && hitTestSegmentForPopup(wx0, wy0) == null) hideRoadTip();

            // Ctrl+Click trong BẤT KỲ mode nào → chọn đường để sửa
            if (e.isControlDown() && currentMode != InteractionType.DELETE) {
                RoadSegment clicked = hitTestSegment(wx0, wy0);
                selectedSegment = clicked;
                if (onSegmentSelected != null) onSegmentSelected.accept(clicked);
                redraw();
                if (clicked != null) return; // đừng thực hiện action mode
            }

            if (currentMode == InteractionType.PAN) {
                // Click trái trong PAN mode: chọn đường để sửa (không chọn arm stubs)
                double wx2 = toWorldX(e.getX()), wy2 = toWorldY(e.getY());
                RoadSegment clicked = hitTestSegment(wx2, wy2);
                if (clicked != null && clicked.isConnector()) clicked = null; // bỏ qua stub
                if (clicked != null) {
                    selectedSegment = clicked;
                    redraw();
                    // Thông báo App cập nhật nút
                    if (onSegmentSelected != null) onSegmentSelected.accept(clicked);
                    return;
                } else {
                    selectedSegment = null;
                    if (onSegmentSelected != null) onSegmentSelected.accept(null);
                    redraw();
                }
                lastMouseX = e.getX(); lastMouseY = e.getY();

            } else if (currentMode == InteractionType.DELETE) {
                if (hoveredSegment != null) {
                    RoadSegment removed = hoveredSegment;
                    saveSnapshot();
                    if (controller != null) {
                        controller.removeSegmentLight(removed);
                        controller.removeVehiclesOnSegment(removed);
                    }
                    if (selectedSegment == removed) selectedSegment = null;
                    if (tipSegment == removed) hideRoadTip();
                    network.removeSegment(removed);
                    hoveredSegment = null; updateRenderData();
                } else if (hoveredIntersection != null) {
                    Intersection removed = hoveredIntersection;
                    saveSnapshot();
                    if (controller != null) {
                        controller.removeVehiclesInIntersection(removed);
                    }
                    network.removeIntersection(removed);
                    hoveredIntersection = null; updateRenderData();
                }

            } else if (currentMode == InteractionType.DELETE_LIGHT) {
                if (hoveredLight != null && controller != null) {
                    boolean mainAtEnd = hoveredLight.isAtEnd();
                    RoadSegment seg = hoveredLight.getSegment();
                    
                    List<com.myteam.traffic.light.SegmentLight> backupLights = new java.util.ArrayList<>(controller.getAllSegmentLights());
                    controller.removeSegmentLight(seg, mainAtEnd);
                    
                    undoStack.push(() -> {
                        controller.removeSegmentLight(seg, mainAtEnd);
                        for (com.myteam.traffic.light.SegmentLight sl : backupLights) {
                            controller.addSegmentLight(sl);
                        }
                    });
                    hoveredLight = null;
                    redraw();
                }

            } else if (currentMode == InteractionType.PLACE_INTERSECTION) {
                double wx = toWorldX(e.getX()), wy = toWorldY(e.getY());
                // Snap vào endpoint đường gần nhất nếu có (để đặt junction sát đầu đường)
                double[] ep = snapToEndpoint(wx, wy, 50.0 / scale, Double.MAX_VALUE, Double.MAX_VALUE);
                if (ep != null) { wx = ep[0]; wy = ep[1]; }
                else {
                    Intersection near = network.findNearestIntersection(wx, wy, 40.0 / scale);
                    if (near != null) { wx = near.getCenterX(); wy = near.getCenterY(); }
                }
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
            } else if (currentMode == InteractionType.EDIT_MARKINGS) {
                // Click trái ra ngoài đường → thoát, restore mode cũ
                double wx1 = toWorldX(e.getX()), wy1 = toWorldY(e.getY());
                if (hitTestSegmentForPopup(wx1, wy1) == null) {
                    selectedSegment = null;
                    setInteractionType(previousMode);
                    if (onMarkingModeEntered != null) onMarkingModeEntered.accept(null);
                }
            }
        });

        // MOUSE_DRAGGED
        addEventHandler(MouseEvent.MOUSE_DRAGGED, e -> {
            if (e.getButton() == MouseButton.SECONDARY) {
                // Không pan khi popup đang pin — tránh bản đồ trôi lúc người dùng giữ chuột phải để đọc
                if (tipPinned) return;
                translateX += e.getX() - lastMouseX;
                translateY += e.getY() - lastMouseY;
                lastMouseX = e.getX(); lastMouseY = e.getY();
                redraw();
                return;
            }
            if (e.getButton() != MouseButton.PRIMARY) return;

            if (currentMode == InteractionType.PAN) {
                double dX = e.getX() - lastMouseX;
                double dY = e.getY() - lastMouseY;
                translateX += dX;
                translateY += dY;
                lastMouseX = e.getX(); lastMouseY = e.getY();
                // Nếu popup đang pinned, di nó theo world (giữ vị trí tương đối)
                if (tipPinned && roadInfoPopup.isShowing()) {
                    roadInfoPopup.setAnchorX(roadInfoPopup.getAnchorX() + dX);
                    roadInfoPopup.setAnchorY(roadInfoPopup.getAnchorY() + dY);
                }
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

            // Roundabout không còn được trigger từ số làn nữa — dùng mode PLACE_INTERSECTION

            List<Lane> newLanes = (laneFactory != null)
                    ? laneFactory.apply(currentLaneConfig)
                    : SimulationApp.createLanes(currentLaneConfig);

            if (autoIntersect) {
                commitWithIntersections(drawStartX, drawStartY, drawCurrentX, drawCurrentY, newLanes);
            } else {
                network.addSegment(makeSegment(drawStartX, drawStartY, drawCurrentX, drawCurrentY, newLanes));
                // Đồng bộ arm stubs tại 2 endpoint ngay cả khi không autoIntersect
                syncStubLanesAt(drawStartX, drawStartY, newLanes);
                syncStubLanesAt(drawCurrentX, drawCurrentY, newLanes);
            }
            updateRenderData();
        });
    }

    /**
     * Tạo RoadSegment hoặc HighwaySegment tuỳ theo highwayMode.
     */
    private RoadSegment makeSegment(double x1, double y1, double x2, double y2, List<Lane> lanes) {
        if (highwayMode) {
            int minLanes = emergencyLane ? 3 : 2;
            // Đảm bảo đủ làn — highway cần tối thiểu 2 (hoặc 3 nếu có làn khẩn cấp)
            List<Lane> paddedLanes = new ArrayList<>(lanes);
            while (paddedLanes.size() < minLanes) {
                int idx = paddedLanes.size();
                Lane.Direction dir = (idx % 2 == 0) ? Lane.Direction.FORWARD : Lane.Direction.BACKWARD;
                paddedLanes.add(new Lane(idx, dir, 3.5,
                        java.util.EnumSet.of(Lane.VehicleCategory.CAR, Lane.VehicleCategory.BUS),
                        java.util.EnumSet.of(Lane.Movement.STRAIGHT),
                        Lane.MarkingType.DASHED, Lane.MarkingType.DASHED));
            }
            return new com.myteam.traffic.model.infrastructure.HighwaySegment(
                    x1, y1, x2, y2, paddedLanes, highwayMinSpeed, emergencyLane);
        }
        return new RoadSegment(x1, y1, x2, y2, lanes);
    }

    /**
     * Tạo đường mới từ (x1,y1) đến (x2,y2), tự động cắt và tạo Intersection
     * tại mọi điểm giao với đường hiện có.
     *
     * Quy tắc quan trọng:
     * - Nếu giao điểm nằm gần một Intersection có sẵn → đường mới đi QUA intersection đó,
     * KHÔNG tạo intersection mới, KHÔNG cắt thêm các segment trong intersection đó.
     * - Nếu giao điểm nằm giữa một RoadSegment thông thường → cắt đôi, tạo intersection mới.
     */
    private void commitWithIntersections(double x1, double y1, double x2, double y2, List<Lane> newLanes) {
        List<RoadSegment> snapshot = new ArrayList<>(network.getSegments());

        // ── Bước 1: Thu thập hit points ───────────────────────────────────
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

            // CHÚ Ý: Bỏ qua giới hạn skip điểm đầu/điểm cuối, để cho phép tạo T-junction
            // nếu điểm đầu/cuối của đường mới vẽ nằm chèn lên một con đường có sẵn.
            // if (isSamePoint(cx, cy, x1, y1) || isSamePoint(cx, cy, x2, y2)) continue;

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

        // ── Bước 1.5: Thu thập hit points trực tiếp từ các Intersection ───
        // Bắt các trường hợp đường vẽ đi xuyên qua tâm giao lộ hoặc xuất phát từ tâm
        // mà hàm getIntersectionPointStrict không thể bắt được vì u = 0 hoặc u = 1.
        for (Intersection inter : network.getIntersections()) {
            double cx = inter.getCenterX();
            double cy = inter.getCenterY();
            
            // Tính t trên đoạn thẳng x1,y1 -> x2,y2
            double l2 = (x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2);
            double t = (l2 == 0) ? 0 : Math.max(0, Math.min(1, ((cx - x1) * (x2 - x1) + (cy - y1) * (y2 - y1)) / l2));
            double projX = x1 + t * (x2 - x1);
            double projY = y1 + t * (y2 - y1);
            
            if (Math.hypot(cx - projX, cy - projY) < 10.0) {
                // Có hit với intersection này
                final double hitX = cx, hitY = cy;
                HitGroup tgt = hits.stream()
                        .filter(g -> isSamePoint(g.x, g.y, hitX, hitY))
                        .findFirst().orElse(null);
                if (tgt == null) {
                    tgt = new HitGroup(cx, cy, Math.hypot(cx - x1, cy - y1));
                    tgt.isExistingIntersection = true;
                    tgt.existingIntersection = inter;
                    hits.add(tgt);
                } else {
                    tgt.isExistingIntersection = true;
                    tgt.existingIntersection = inter;
                }
            }
        }

        if (hits.isEmpty()) {
            network.addSegment(new RoadSegment(x1, y1, x2, y2, newLanes));
            return;
        }

        hits.sort((a, b) -> Double.compare(a.dist, b.dist));

        // ── Bước 2: Tạo đường theo từng hit ───────────────────────────────
        double curX = x1, curY = y1;
        GeneralIntersection lastNode = null;

        for (HitGroup group : hits) {
            boolean isStartPoint = isSamePoint(curX, curY, group.x, group.y);

            RoadSegment inPart = null;
            if (!isStartPoint) {
                // Tạo đoạn đường từ vị trí hiện tại đến điểm giao
                inPart = new RoadSegment(curX, curY, group.x, group.y, newLanes);
                network.addSegment(inPart);
                if (lastNode != null) lastNode.connectRoad(inPart, ConnectionPoint.End.START);
            }

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
                    if (atStart) {
                        workNode.connectRoad(crossed, ConnectionPoint.End.START);
                        continue;
                    }
                    if (atEnd) {
                        workNode.connectRoad(crossed, ConnectionPoint.End.END);
                        continue;
                    }
                    if (!network.getSegments().contains(crossed)) continue;

                    // TRƯỚC KHI XÓA: Lưu lại danh sách các giao lộ đang gắn với 'crossed'
                    class SavedConnection {
                        Intersection inter;
                        ConnectionPoint.End end;
                        SavedConnection(Intersection i, ConnectionPoint.End e) { inter = i; end = e; }
                    }
                    List<SavedConnection> saved = new ArrayList<>();
                    for (Intersection inter : network.getIntersections()) {
                        for (ConnectionPoint cp : inter.getConnections()) {
                            if (cp.getSegment() == crossed) {
                                saved.add(new SavedConnection(inter, cp.getEnd()));
                            }
                        }
                    }

                    network.removeSegment(crossed);
                    RoadSegment p1 = new RoadSegment(crossed.getStartX(), crossed.getStartY(), group.x, group.y, crossed.getLanes());
                    RoadSegment p2 = new RoadSegment(group.x, group.y, crossed.getEndX(), crossed.getEndY(), crossed.getLanes());
                    network.addSegment(p1);
                    network.addSegment(p2);
                    
                    // PHỤC HỒI KẾT NỐI
                    for (SavedConnection sc : saved) {
                        if (sc.end == ConnectionPoint.End.START) {
                            sc.inter.connectRoad(p1, ConnectionPoint.End.START);
                        } else {
                            sc.inter.connectRoad(p2, ConnectionPoint.End.END);
                        }
                    }

                    workNode.connectRoad(p1, ConnectionPoint.End.END);
                    workNode.connectRoad(p2, ConnectionPoint.End.START);
                }
            }

            if (inPart != null) {
                workNode.connectRoad(inPart, ConnectionPoint.End.END);
            }
            lastNode = workNode;
            curX = group.x; curY = group.y;
        }

        // Đoạn cuối
        if (!isSamePoint(curX, curY, x2, y2)) {
            RoadSegment tail = new RoadSegment(curX, curY, x2, y2, newLanes);
            network.addSegment(tail);
            if (lastNode != null) lastNode.connectRoad(tail, ConnectionPoint.End.START);
        }

        // ── Bước 3: Đồng bộ số làn arm stubs tại 2 endpoint ───────────────
        // Nếu endpoint của đường mới snap vào đầu/cuối của arm stub (connector=true),
        // thì replace stub để khớp số làn với đường mới → junction fill đúng kích thước
        syncStubLanesAt(x1, y1, newLanes);
        syncStubLanesAt(x2, y2, newLanes);

        // ── Bước 4: Xóa arm stubs đã được "thay thế" bởi đường thật ────────
        // Nếu endpoint của đường mới trùng với endpoint của một stub → stub đó không còn cần nữa
        removeStubsReplacedByRoadAt(x1, y1);
        removeStubsReplacedByRoadAt(x2, y2);
    }

    /**
     * Xóa arm stub (connector=true) có endpoint BẮT ĐẦU tại (wx,wy) —
     * nghĩa là đường thật đã "thế chỗ" stub đó.
     * Không xóa nếu tại (wx,wy) không có intersection (tức stub chưa được nối vào giao lộ nào).
     */
    private void removeStubsReplacedByRoadAt(double wx, double wy) {
        if (network == null) return;
        // Chỉ xóa khi có intersection thật tại điểm này
        Intersection inter = network.findNearestIntersection(wx, wy, 60.0);
        if (inter == null) return;
        double icx = inter.getCenterX(), icy = inter.getCenterY();
        for (RoadSegment seg : new ArrayList<>(network.getSegments())) {
            if (!seg.isConnector()) continue;
            // Stub bắt đầu TỪ tâm giao lộ ra ngoài → đầu START là tâm giao lộ
            boolean startAtCenter = isSamePoint(seg.getStartX(), seg.getStartY(), icx, icy);
            boolean endAtCenter   = isSamePoint(seg.getEndX(),   seg.getEndY(),   icx, icy);
            if (!startAtCenter && !endAtCenter) continue;
            // Kiểm tra đầu ngoài (tip) của stub — nếu có đường thật nối vào đó thì xóa stub
            double tipX = startAtCenter ? seg.getEndX()   : seg.getStartX();
            double tipY = startAtCenter ? seg.getEndY()   : seg.getStartY();
            // Đường thật = không phải connector, có endpoint trùng với tip của stub
            boolean hasRealRoad = network.getSegments().stream()
                    .anyMatch(s -> !s.isConnector() && s != seg &&
                            (isSamePoint(s.getStartX(), s.getStartY(), tipX, tipY) ||
                                    isSamePoint(s.getEndX(),   s.getEndY(),   tipX, tipY)));
            if (hasRealRoad) network.removeSegment(seg);
        }
    }

    /**
     * Nếu tại (wx, wy) có arm stub (connector=true), replace nó bằng bản mới có số làn = newLanes.
     * Intersection radius sẽ tự tính lại từ số làn mới qua getRenderData().
     */
    private void syncStubLanesAt(double wx, double wy, List<Lane> newLanes) {
        // Sync stubs có endpoint tại (wx, wy)
        for (RoadSegment seg : new ArrayList<>(network.getSegments())) {
            if (!seg.isConnector()) continue;
            boolean atStart = isSamePoint(seg.getStartX(), seg.getStartY(), wx, wy);
            boolean atEnd   = isSamePoint(seg.getEndX(),   seg.getEndY(),   wx, wy);
            if (!atStart && !atEnd) continue;
            if (seg.getLaneCount() == newLanes.size()) continue;
            RoadSegment updated = seg.withNewLanes(rebuildLanesMatchingCount(seg.getLanes(), newLanes));
            updated.setConnector(true);
            network.replaceSegment(seg, updated);
        }
        // Nếu endpoint gần tâm intersection → sync TẤT CẢ stubs của intersection đó
        Intersection inter = network.findNearestIntersection(wx, wy, 60.0);
        if (inter == null) return;
        double icx = inter.getCenterX(), icy = inter.getCenterY();
        for (RoadSegment seg : new ArrayList<>(network.getSegments())) {
            if (!seg.isConnector()) continue;
            if (!isSamePoint(seg.getStartX(), seg.getStartY(), icx, icy) &&
                    !isSamePoint(seg.getEndX(),   seg.getEndY(),   icx, icy)) continue;
            if (seg.getLaneCount() == newLanes.size()) continue;
            RoadSegment updated = seg.withNewLanes(rebuildLanesMatchingCount(seg.getLanes(), newLanes));
            updated.setConnector(true);
            network.replaceSegment(seg, updated);
        }
    }

    /**
     * Tạo danh sách làn mới: giữ pattern chiều (FWD/BWD) từ oldLanes, đổi số lượng theo newLanes.
     * Dùng thông tin width + vehicle + movement từ newLanes làm template.
     */
    private List<Lane> rebuildLanesMatchingCount(List<Lane> oldLanes, List<Lane> newLanes) {
        List<Lane> result = new ArrayList<>();
        int n = newLanes.size();
        for (int i = 0; i < n; i++) {
            Lane template = newLanes.get(i);
            // Giữ chiều đường từ oldLanes nếu có, không thì dùng template
            Lane.Direction dir = (i < oldLanes.size())
                    ? oldLanes.get(i).getDirection()
                    : template.getDirection();
            result.add(new Lane(i, dir, template.getWidth(),
                    template.getAllowedVehicles(), template.getAllowedMovements(),
                    template.getLeftMarking(), template.getRightMarking()));
        }
        return result;
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
     * Guard radius nhỏ (25 world units) — không phụ thuộc vào scale.
     */
    private void placeIntersectionAt(double wx, double wy) {
        if (network.findNearestIntersection(wx, wy, 25.0) != null) return;

        com.myteam.traffic.model.infrastructure.intersection.Intersection inter;
        int armCount;
        switch (intersectionTypeToPlace) {
            case "3"  -> { inter = new com.myteam.traffic.model.infrastructure.intersection.ThreeWayIntersection(wx, wy); armCount = 3; }
            case "5"  -> { inter = new com.myteam.traffic.model.infrastructure.intersection.FiveWayIntersection(wx, wy);  armCount = 5; }
            case "ROUNDABOUT_S" -> { inter = new com.myteam.traffic.model.infrastructure.intersection.RoundaboutIntersection(wx, wy, roundaboutBranches, roundaboutRadius);        armCount = roundaboutBranches; }
            case "ROUNDABOUT_L" -> { inter = new com.myteam.traffic.model.infrastructure.intersection.RoundaboutIntersection(wx, wy, roundaboutBranches, roundaboutRadius * 1.6); armCount = roundaboutBranches; }
            default -> { inter = new com.myteam.traffic.model.infrastructure.intersection.FourWayIntersection(wx, wy); armCount = 4; }
        }
        network.addIntersection(inter);

        // Arm stubs: chiều rộng 1 làn = 20 world units → 5 làn = 100 world units half
        // Stub phải đủ dài để junction radius (minArmLen * 0.4) > halfWidth đường
        // halfWidth = laneCount * 20 / 2. Cần: stubLen * 0.4 > halfWidth → stubLen > halfWidth * 2.5
        int laneCount = currentLaneConfig;
        double halfWidth = laneCount * 20.0 / 2.0;
        double stubLen  = Math.max(150.0, halfWidth * 3.0); // đủ dài cho mọi độ rộng

        List<Lane> stubLanes = (laneFactory != null)
                ? laneFactory.apply(laneCount)
                : SimulationApp.createLanes(laneCount);

        for (int i = 0; i < armCount; i++) {
            // Góc thực tế theo từng loại giao lộ:
            // Ngã 3: Bắc + Tây-Nam + Đông-Nam (hình chữ T ngược)
            // Ngã 4: Bắc Đông Nam Tây
            // Ngã 5: Bắc + Tây-Bắc + Đông-Bắc + Tây-Nam + Đông-Nam
            // Ngã 6: đều 60°
            double angleDeg;
            if (armCount == 3) {
                double[] angles3 = {-90, 180, 0}; // Bắc, Tây, Đông (chữ T)
                angleDeg = angles3[i];
            } else if (armCount == 5) {
                double[] angles5 = {-90, -162, -18, 126, 54}; // Bắc + 4 nhánh xòe ra
                angleDeg = angles5[i];
            } else {
                angleDeg = -90 + 360.0 / armCount * i;
            }
            double angle = Math.toRadians(angleDeg);
            double endX = wx + Math.cos(angle) * stubLen;
            double endY = wy + Math.sin(angle) * stubLen;
            RoadSegment stub = makeSegment(wx, wy, endX, endY, new ArrayList<>(stubLanes));
            stub.setConnector(true);
            network.addSegment(stub);
            try { inter.connectRoad(stub, com.myteam.traffic.model.infrastructure.ConnectionPoint.End.START); }
            catch (IllegalStateException ignored) {}
        }
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

    /**
     * Dựng sẵn một mạng lưới đường phức tạp gồm 9 giao lộ để demo nhanh.
     */
    public void loadDemoNetwork() {
        network.clear();
        if (controller != null) {
            controller.clearVehicles();
            controller.clearLights();
        }
        
        // Tạo sẵn 9 nút giao để "đón lõng" các đường vẽ, giúp chúng bắt dính hoàn hảo tại các điểm mút và tạo bo góc chữ L, chữ T
        // Góc (2 ngã):
        network.addIntersection(new com.myteam.traffic.model.infrastructure.intersection.GeneralIntersection(200, 200, 2));
        network.addIntersection(new com.myteam.traffic.model.infrastructure.intersection.GeneralIntersection(800, 200, 2));
        network.addIntersection(new com.myteam.traffic.model.infrastructure.intersection.GeneralIntersection(200, 800, 2));
        network.addIntersection(new com.myteam.traffic.model.infrastructure.intersection.GeneralIntersection(800, 800, 2));
        
        // Biên (3 ngã):
        network.addIntersection(new com.myteam.traffic.model.infrastructure.intersection.GeneralIntersection(500, 200, 3));
        network.addIntersection(new com.myteam.traffic.model.infrastructure.intersection.GeneralIntersection(200, 500, 3));
        network.addIntersection(new com.myteam.traffic.model.infrastructure.intersection.GeneralIntersection(800, 500, 3));
        network.addIntersection(new com.myteam.traffic.model.infrastructure.intersection.GeneralIntersection(500, 800, 3));
        
        // Trung tâm (4 ngã):
        network.addIntersection(new com.myteam.traffic.model.infrastructure.intersection.GeneralIntersection(500, 500, 4));

        // 1. Vẽ các trục ngang (chỉ vẽ đúng chiều dài thực tế để không dư nét tạo thành ngã tư)
        commitWithIntersections(200, 200, 800, 200, SimulationApp.createLanes(2)); // Ngang trên
        commitWithIntersections(200, 500, 800, 500, SimulationApp.createLanes(2)); // Ngang giữa
        commitWithIntersections(200, 800, 800, 800, SimulationApp.createLanes(2)); // Ngang dưới

        // 2. Vẽ các trục dọc
        commitWithIntersections(200, 200, 200, 800, SimulationApp.createLanes(2)); // Dọc trái
        commitWithIntersections(500, 200, 500, 800, SimulationApp.createLanes(2)); // Dọc giữa
        commitWithIntersections(800, 200, 800, 800, SimulationApp.createLanes(2)); // Dọc phải

        // 3. Thêm tự động đèn giao thông (có đếm ngược) cho các giao lộ từ ngã 3 trở lên
        if (controller != null) {
            for (Intersection inter : network.getIntersections()) {
                if (inter.getConnections().size() >= 3) {
                    ConnectionPoint mainCp = inter.getConnections().get(0);
                    com.myteam.traffic.model.infrastructure.RoadSegment mainSeg = mainCp.getSegment();
                    boolean mainSAtEnd = (mainCp.getEnd() == ConnectionPoint.End.END);
                    double mainAngle = mainSAtEnd ? 
                        Math.atan2(mainSeg.getEndY() - mainSeg.getStartY(), mainSeg.getEndX() - mainSeg.getStartX()) :
                        Math.atan2(mainSeg.getStartY() - mainSeg.getEndY(), mainSeg.getStartX() - mainSeg.getEndX());

                    for (ConnectionPoint cp : inter.getConnections()) {
                        com.myteam.traffic.model.infrastructure.RoadSegment s = cp.getSegment();
                        boolean sAtEnd = (cp.getEnd() == ConnectionPoint.End.END);
                        
                        double sAngle = sAtEnd ? 
                            Math.atan2(s.getEndY() - s.getStartY(), s.getEndX() - s.getStartX()) :
                            Math.atan2(s.getStartY() - s.getEndY(), s.getStartX() - s.getEndX());

                        double diff = Math.abs(mainAngle - sAngle);
                        while (diff > Math.PI) diff -= 2 * Math.PI;
                        diff = Math.abs(diff);

                        // Đồng bộ đèn: Đỏ = Xanh + Vàng (23 = 20 + 3)
                        com.myteam.traffic.light.TrafficLight tLight = new com.myteam.traffic.light.CountdownLight(23, 20, 3);
                        
                        if (diff <= Math.PI / 4 || Math.abs(diff - Math.PI) <= Math.PI / 4) {
                            tLight.setInitialState(com.myteam.traffic.light.TrafficLightState.GREEN, 20);
                        } else {
                            tLight.setInitialState(com.myteam.traffic.light.TrafficLightState.RED, 23);
                        }
                        
                        controller.addSegmentLight(new com.myteam.traffic.light.SegmentLight(s, tLight, sAtEnd));
                    }
                }
            }
        }
        
        updateRenderData();
    }

    public void setController(com.myteam.traffic.controller.TrafficController controller) {
        this.controller = controller;
    }
}
