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

    public enum InteractionType { PAN, DRAW_ROAD, EDIT_MARKINGS, PLACE_INTERSECTION, DELETE }

    /**
     * Loáº¡i giao lá»™ sáº½ Ä‘Æ°á»£c Ä‘áº·t khi ngÆ°á»i dÃ¹ng click trong mode PLACE_INTERSECTION.
     * "3T","3Y"=NgÃ£ ba T/Y; "4"=NgÃ£ tÆ°; "5"=NgÃ£ nÄƒm; "ROUNDABOUT_S/L"=VÃ²ng xuyáº¿n.
     */
    private String intersectionTypeToPlace = "4";
    private double roundaboutRadius        = 60.0;
    private int    roundaboutBranches      = 4;
    private java.util.function.IntFunction<java.util.List<Lane>> laneFactory = null;

    // â”€â”€ Cáº¥u hÃ¬nh lÃ n nÃ¢ng cao â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private double  laneWidth       = 3.5;
    private boolean highwayMode     = false;
    private double  highwayMinSpeed = 60.0;
    private boolean emergencyLane   = false;
    private boolean allowHeavy      = false;

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

    private com.myteam.traffic.controller.TrafficController controller;
    private final VehicleRenderer vehicleRenderer = new VehicleRenderer();

    // ── Mô phỏng (spawn + tick) ────────────────────────────────
    private VehicleSpawner spawner;
    private boolean simulationRunning = false;

    private final javafx.animation.AnimationTimer simLoop = new javafx.animation.AnimationTimer() {
        private long lastNanos = -1;
        @Override public void handle(long now) {
            if (lastNanos < 0) { lastNanos = now; return; }
            double dt = Math.min((now - lastNanos) / 1_000_000_000.0, 0.1);
            lastNanos = now;
            if (spawner != null) spawner.tick(dt);
            if (controller != null) controller.tick(dt);
            redraw();
        }
        @Override public void start() { lastNanos = -1; super.start(); }
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

    /** Chạy 1 tick thủ công (vd. ngay sau khi thêm xe lúc đang pause) để cập nhật hiển thị. */
    public void stepOnce() {
        if (controller != null) controller.tick(0.0);
        redraw();
    }

    // â”€â”€ Redraw throttle: gá»™p nhiá»u yÃªu cáº§u váº½ láº¡i trong 1 frame â”€
    private boolean redrawPending = false;
    private final javafx.animation.AnimationTimer redrawTimer = new javafx.animation.AnimationTimer() {
        @Override public void handle(long now) {
            if (redrawPending) { redrawPending = false; redrawNow(); }
        }
    };

    private static final double SNAP_GRID = 100.0;

    // â”€â”€ Hover state (EDIT_MARKINGS mode) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private HoverResult hoveredBoundary = null;

    // â”€â”€ Hover state (DELETE mode) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private RoadSegment  hoveredSegment      = null;
    private Intersection hoveredIntersection = null;

    /** GÃ³i dá»¯ liá»‡u mÃ´ táº£ ranh giá»›i Ä‘ang Ä‘Æ°á»£c hover. */
    private static class HoverResult {
        final RoadSegment seg;
        final int         boundaryIndex; // ranh giá»›i ná»™i bá»™ (1 .. laneCount-1)
        final double      screenX, screenY; // tá»a Ä‘á»™ mÃ n hÃ¬nh cá»§a Ä‘iá»ƒm giá»¯a váº¡ch

        HoverResult(RoadSegment seg, int boundaryIndex, double screenX, double screenY) {
            this.seg = seg;
            this.boundaryIndex = boundaryIndex;
            this.screenX = screenX;
            this.screenY = screenY;
        }
    }

    /**
     * Callback Ä‘á»ƒ App Ä‘á»“ng bá»™ toolbar khi view thay Ä‘á»•i tráº¡ng thÃ¡i EDIT_MARKINGS.
     * Tham sá»‘: InteractionType trÆ°á»›c Ä‘Ã³ náº¿u vá»«a vÃ o EDIT_MARKINGS, null náº¿u vá»«a thoÃ¡t.
     */
    private java.util.function.Consumer<InteractionType> onMarkingModeEntered = null;
    public void setOnMarkingModeEntered(java.util.function.Consumer<InteractionType> cb) { this.onMarkingModeEntered = cb; }

    /** Mode trÆ°á»›c khi vÃ o EDIT_MARKINGS â€” Ä‘á»ƒ restore khi thoÃ¡t. */
    private InteractionType previousMode = InteractionType.PAN;

    /**
     * Flag bá» qua 1 MOUSE_PRESSED tiáº¿p theo â€” dÃ¹ng khi popup Ä‘Ã³ng vÃ  event
     * "click Ä‘á»ƒ Ä‘Ã³ng popup" bá»‹ forward xuá»‘ng canvas gÃ¢y pan/select khÃ´ng mong muá»‘n.
     */
    private boolean ignoreNextPress = false;
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

    // â”€â”€ Constructor â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    public SimulationView(double width, double height) {
        super(width, height);
        this.renderer   = new InfrastructureRenderer(this);
        this.translateX = width  / 2;
        this.translateY = height / 2;
        setupEvents();
        // áº¨n popup khi cá»­a sá»• máº¥t focus
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

    // â”€â”€ Setters â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    public void setNetwork(RoadNetwork network)       { this.network = network; updateRenderData(); }
    public void setCurrentLaneConfig(int lanes)       { this.currentLaneConfig = lanes; }
    public void setAutoIntersect(boolean auto)        { this.autoIntersect = auto; }
    public void setShowGrid(boolean show)             { this.showGrid = show; redraw(); }
    public void setShowLabels(boolean show)           { this.showLabels = show; redraw(); }
    public void setIntersectionTypeToPlace(String t)  { this.intersectionTypeToPlace = t; }
    public void setLaneFactory(java.util.function.IntFunction<java.util.List<Lane>> f) { this.laneFactory = f; }

    // â”€â”€ Cáº¥u hÃ¬nh lÃ n Ä‘Æ°á»ng nÃ¢ng cao â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    /** Chiá»u rá»™ng má»—i lÃ n (mÃ©t, máº·c Ä‘á»‹nh 3.5). Ãp dá»¥ng cho Ä‘Æ°á»ng váº½ tiáº¿p theo. */
    public void setLaneWidth(double w)                { this.laneWidth = Math.max(2.0, Math.min(6.0, w)); }
    /** BÃ¡n kÃ­nh vÃ²ng xuyáº¿n (world units). */
    public void setRoundaboutRadius(double r)         { this.roundaboutRadius = Math.max(40, r); }
    // â”€â”€ Selected segment (click trong PAN mode Ä‘á»ƒ chá»n Ä‘Æ°á»ng sá»­a) â”€â”€
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
    /** Sá»‘ nhÃ¡nh vÃ²ng xuyáº¿n. */
    public void setRoundaboutBranches(int b)          { this.roundaboutBranches = Math.max(3, Math.min(8, b)); }
    /** ÄÆ°á»ng cao tá»‘c: true = dÃ¹ng HighwaySegment. */
    public void setHighwayMode(boolean hw)            { this.highwayMode = hw; }
    /** Tá»‘c Ä‘á»™ tá»‘i thiá»ƒu cao tá»‘c (km/h). */
    public void setHighwayMinSpeed(double s)          { this.highwayMinSpeed = s; }
    /** CÃ³ lÃ n kháº©n cáº¥p khÃ´ng. */
    public void setEmergencyLane(boolean el)          { this.emergencyLane = el; }
    /** Cho phÃ©p xe buÃ½t/xe táº£i. */
    public void setAllowHeavy(boolean allow)          { this.allowHeavy = allow; }

    public void setInteractionType(InteractionType m) {
        this.currentMode = m;
        this.isDrawing   = false;
        this.hoveredBoundary    = null;
        this.hoveredSegment     = null;
        this.hoveredIntersection = null;
        // Äáº·t vá»‹ trÃ­ preview vá» tÃ¢m canvas náº¿u chuá»™t chÆ°a vÃ o canvas
        if (lastMouseX <= 0) lastMouseX = getWidth() / 2;
        if (lastMouseY <= 0) lastMouseY = getHeight() / 2;
        redraw();
    }

    private void updateRenderData() { this.data = (network != null) ? network.getRenderData() : null; redraw(); }
    public void resetView()  { scale = 1.0; translateX = getWidth()/2; translateY = getHeight()/2; redraw(); }
    public void zoomIn()     { scale *= 1.2; redraw(); }
    public void zoomOut()    { scale /= 1.2; redraw(); }

    /** YÃªu cáº§u váº½ láº¡i â€” Ä‘Æ°á»£c gá»™p láº¡i trong 1 frame, trÃ¡nh váº½ nhiá»u láº§n/frame khi zoom/pan nhanh. */
    public void redraw() { redrawPending = true; redrawTimer.start(); }

    public double getViewScale() { return scale; }
    public InteractionType getCurrentMode() { return currentMode; }
    public double getViewX()     { return translateX; }
    public double getViewY()     { return translateY; }

    // â”€â”€ Coordinate transforms â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
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
            // Cháº¥p nháº­n náº¿u Ä‘iá»ƒm endpoint náº±m trong hoáº·c sÃ¡t bÃ¡n kÃ­nh nÃºt giao
            if (d <= inter.radius + 5.0 && d < bestDist) {
                bestDist = d;
                bestR = inter.radius;
            }
        }
        return bestR;
    }



    /** Hit-test: tÃ¬m RoadSegment gáº§n con trá» nháº¥t (dÃ¹ng cho DELETE mode). */
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
            // LÃ¹i vÃ o trong 8% á»Ÿ 2 Ä‘áº§u Ä‘á»ƒ trÃ¡nh Ä‘Ã¨ vÃ¹ng giao lá»™
            t = Math.max(0.08, Math.min(0.92, t));
            double projX = sx + t*dx, projY = sy + t*dy;
            double d = Math.hypot(worldX - projX, worldY - projY);
            // VÃ¹ng nháº­n diá»‡n = 60% chiá»u rá»™ng thá»±c (rá»™ng hÆ¡n cÅ© Ã—1.4 nhÆ°ng khÃ´ng quÃ¡ toÃ n bá»™)
            double halfWidth = seg.getLaneCount() * 20.0 / 2.0 * 1.4;
            if (d <= halfWidth && d < bestDist) { bestDist = d; result = seg; }
        }
        return result;
    }

    /**
     * PhiÃªn báº£n hit-test rá»™ng hÆ¡n dÃ¹ng cho hover + chuá»™t pháº£i pin popup.
     * VÃ¹ng nháº­n diá»‡n = toÃ n bá»™ chiá»u rá»™ng Ä‘Æ°á»ng + padding 12 world units má»—i bÃªn,
     * vÃ  khÃ´ng bá»‹ giá»›i háº¡n 8% á»Ÿ 2 Ä‘áº§u (Ä‘á»ƒ hover Ä‘áº§u Ä‘Æ°á»ng váº«n hiá»‡n popup).
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
            t = Math.max(0.0, Math.min(1.0, t)); // toÃ n bá»™ chiá»u dÃ i Ä‘Æ°á»ng
            double projX = sx + t*dx, projY = sy + t*dy;
            double d = Math.hypot(worldX - projX, worldY - projY);
            // ToÃ n bá»™ ná»­a chiá»u rá»™ng thá»±c + 12 world units padding
            double halfWidth = seg.getLaneCount() * laneWidth / 2.0 + 12.0;
            if (d <= halfWidth && d < bestDist) { bestDist = d; result = seg; }
        }
        return result;
    }

    /**
     * Snap Ä‘iá»ƒm world vÃ o Ä‘iá»ƒm gáº§n nháº¥t náº±m TRÃŠN THÃ‚N Ä‘oáº¡n Ä‘Æ°á»ng (khÃ´ng chá»‰ endpoint).
     * Tráº£ vá» [snapX, snapY] náº¿u tÃ¬m tháº¥y, null náº¿u khÃ´ng.
     * excludeX/excludeY: Ä‘iá»ƒm xuáº¥t phÃ¡t â€” bá» qua náº¿u snap trÃ¹ng vá»›i nÃ³.
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
     * TÃ¬m endpoint (Ä‘iá»ƒm Ä‘áº§u/cuá»‘i) cá»§a Ä‘Æ°á»ng hiá»‡n cÃ³ gáº§n nháº¥t trong pháº¡m vi radius.
     * Tráº£ vá» [x, y] náº¿u tÃ¬m tháº¥y, null náº¿u khÃ´ng.
     * excludeX/excludeY: Ä‘iá»ƒm Ä‘ang váº½ tá»« Ä‘Ã³ â€” khÃ´ng snap vÃ o chÃ­nh nÃ³.
     */
    private double[] snapToEndpoint(double worldX, double worldY, double radius,
                                    double excludeX, double excludeY) {
        if (network == null) return null;
        double best = radius;
        double[] result = null;
        for (RoadSegment road : network.getSegments()) {
            double sx = road.getStartX(), sy = road.getStartY();
            double ex = road.getEndX(),   ey = road.getEndY();
            // Bá» qua Ä‘iá»ƒm trÃ¹ng vá»›i Ä‘iá»ƒm xuáº¥t phÃ¡t (trÃ¡nh snap vÃ o chÃ­nh Ä‘iá»ƒm Ä‘ang váº½ tá»« Ä‘Ã³)
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

    // â”€â”€ Connection count (for stop lines) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // Chá»‰ tráº£ vá» sá»‘ > 1 khi táº¡i Ä‘iá»ƒm Ä‘Ã³ cÃ³ Intersection tháº­t sá»± Ä‘Æ°á»£c Ä‘Äƒng kÃ½.
    // TrÃ¡nh váº½ stop line khi 2 Ä‘Æ°á»ng chá»‰ ná»‘i Ä‘uÃ´i nhau tháº³ng hÃ ng.
    private int countConnections(double x, double y) {
        if (network == null) return 0;
        // Kiá»ƒm tra cÃ³ Intersection táº¡i Ä‘iá»ƒm nÃ y khÃ´ng
        Intersection inter = network.findNearestIntersection(x, y, 5.0);
        if (inter == null) return 1; // KhÃ´ng cÃ³ ngÃ£ tÆ° â†’ khÃ´ng váº½ stop line
        // Äáº¿m sá»‘ Ä‘Æ°á»ng káº¿t ná»‘i vÃ o intersection Ä‘Ã³
        return inter.getRoadCount();
    }

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // REDRAW
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    /** Thá»±c sá»± váº½ láº¡i ngay láº­p tá»©c (gá»i ná»™i bá»™ tá»« AnimationTimer). */
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

            renderer.setRenderData(data); // inject Ä‘á»ƒ computeMarkClipOffset dÃ¹ng
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
            // Highlight Ä‘Æ°á»ng Ä‘Ã£ Ä‘Æ°á»£c chá»n (click trong PAN mode)
            if (selectedSegment != null) renderer.drawSelectedHighlight(gc, selectedSegment);

            // DELETE mode: highlight Ä‘á» Ä‘oáº¡n Ä‘Æ°á»ng / nÃºt giao Ä‘ang hover
            if (currentMode == InteractionType.DELETE) {
                if (hoveredSegment != null) renderer.drawSegmentHighlight(gc, hoveredSegment);
                if (hoveredIntersection != null) renderer.drawIntersectionHighlight(gc, hoveredIntersection);
            }
        }

        // -- Ve xe (controller.getVehicles()) --
        if (controller != null) {
            gc.save();
            gc.translate(translateX, translateY);
            gc.scale(scale, scale);
            for (Vehicle v : controller.getVehicles()) {
                vehicleRenderer.render(gc, v, true);
            }
            gc.restore();
        }

        if (currentMode == InteractionType.DRAW_ROAD && isDrawing) {
            double sx = toScreenX(drawStartX), sy = toScreenY(drawStartY);
            double ex = toScreenX(drawCurrentX), ey = toScreenY(drawCurrentY);

            // â”€â”€ AXIS GUIDE: 2 Ä‘Æ°á»ng trá»¥c theo hÆ°á»›ng kÃ©o váº½ â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            // TÃ­nh gÃ³c hÆ°á»›ng tá»« Ä‘iá»ƒm báº¯t Ä‘áº§u â†’ Ä‘iá»ƒm hiá»‡n táº¡i
            double axDx = ex - sx, axDy = ey - sy;
            double axLen = Math.hypot(axDx, axDy);
            if (axLen > 2) { // chá»‰ váº½ khi Ä‘Ã£ kÃ©o Ä‘á»§ xa
                double ux = axDx / axLen, uy = axDy / axLen; // unit vector theo hÆ°á»›ng váº½
                double big = Math.max(getWidth(), getHeight()) * 3; // Ä‘á»§ dÃ i ra ngoÃ i mÃ n hÃ¬nh

                gc.save();
                gc.setStroke(Color.web("#e8d44d", 0.28));
                gc.setLineWidth(1.2);
                gc.setLineDashes(14, 10);

                // Trá»¥c qua Ä‘iá»ƒm Báº®T Äáº¦U â€” kÃ©o cáº£ 2 chiá»u
                gc.strokeLine(sx - ux * big, sy - uy * big, sx + ux * big, sy + uy * big);

                // Trá»¥c qua Ä‘iá»ƒm HIá»†N Táº I â€” kÃ©o cáº£ 2 chiá»u
                gc.strokeLine(ex - ux * big, ey - uy * big, ex + ux * big, ey + uy * big);

                gc.setLineDashes(null);
                gc.restore();
            }

            // â”€â”€ PREVIEW Ä‘Æ°á»ng Ä‘ang váº½ â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            gc.setStroke(Color.web("#e8d44d", 0.75));
            gc.setLineWidth(Math.max(2, (currentLaneConfig * 4) * scale));
            gc.setLineCap(StrokeLineCap.BUTT);
            gc.strokeLine(sx, sy, ex, ey);

            // Cháº¥m nhá» táº¡i Ä‘iá»ƒm báº¯t Ä‘áº§u vÃ  Ä‘iá»ƒm hiá»‡n táº¡i
            gc.setFill(Color.web("#e8d44d", 0.9));
            gc.fillOval(sx - 3, sy - 3, 6, 6);
            gc.fillOval(ex - 3, ey - 3, 6, 6);
        }

        // Ghost preview cho mode PLACE_INTERSECTION
        if (currentMode == InteractionType.PLACE_INTERSECTION && lastMouseX > 0) {
            double wx = toWorldX(lastMouseX), wy = toWorldY(lastMouseY);
            // Snap preview vÃ o endpoint gáº§n nháº¥t náº¿u cÃ³
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

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // HOVER HIT-TEST (EDIT_MARKINGS mode)
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    /**
     * DÃ² tÃ¬m ranh giá»›i lÃ n gáº§n con trá» nháº¥t.
     * Tráº£ vá» HoverResult náº¿u con trá» Ä‘á»§ gáº§n (< HOVER_THRESHOLD Ä‘Æ¡n vá»‹ tháº¿ giá»›i),
     * ngÆ°á»£c láº¡i tráº£ vá» null.
     *
     * Thuáº­t toÃ¡n: xoay Ä‘iá»ƒm click vÃ o há»‡ tá»a Ä‘á»™ cá»¥c bá»™ cá»§a Ä‘Æ°á»ng,
     * sau Ä‘Ã³ so sÃ¡nh localY vá»›i vá»‹ trÃ­ tá»«ng ranh giá»›i.
     */
    private static final double HOVER_THRESHOLD = 8.0; // Ä‘Æ¡n vá»‹ tháº¿ giá»›i

    private HoverResult hitTestBoundary(double worldX, double worldY) {
        if (network == null) return null;

        HoverResult best = null;
        double bestDist = HOVER_THRESHOLD;

        // Chá»‰ kiá»ƒm tra ranh giá»›i trÃªn Ä‘Æ°á»ng Ä‘ang Ä‘Æ°á»£c chá»n (selectedSegment)
        // KhÃ´ng cho phÃ©p sá»­a váº¡ch cá»§a Ä‘Æ°á»ng khÃ¡c khi Ä‘ang á»Ÿ EDIT_MARKINGS
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

            // Sá»¬A Lá»–I: Chuyá»ƒn Ä‘á»•i 3.5m thÃ nh 20 pixels tháº¿ giá»›i
            double totalWorldWidth = lanes.stream().mapToDouble(l -> (l.getWidth() / 3.5) * 20.0).sum();

            if (Math.abs(localY) > totalWorldWidth / 2.0 + HOVER_THRESHOLD) continue;

            double boundaryPos = -totalWorldWidth / 2.0;
            for (int i = 0; i < lanes.size() - 1; i++) {
                boundaryPos += (lanes.get(i).getWidth() / 3.5) * 20.0; // Sá»­ dá»¥ng scale 20.0

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

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // POPUP MENU (EDIT_MARKINGS mode)
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    // â”€â”€ Road info popup â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // Hover  â†’ info text Ä‘i theo chuá»™t, khÃ´ng cÃ³ nÃºt Sá»­a
    // Click trÃ¡i â†’ popup Ä‘á»©ng yÃªn, hiá»‡n nÃºt âœ Sá»­a
    // Máº¥t focus window â†’ tá»± áº©n
    private final Popup  roadInfoPopup         = new Popup();
    private final Label  roadInfoLabel         = new Label();
    private final Button roadInfoEditBtn       = new Button("âœ  Sá»­a Ä‘Æ°á»ng nÃ y");
    private final Button roadInfoEditMarkBtn   = new Button("ðŸŽ¨  Sá»­a váº¡ch káº»");
    private RoadSegment  tipSegment  = null;
    private boolean      tipPinned   = false;   // true = chuá»™t pháº£i Ä‘Ã£ pin

    {
        roadInfoLabel.setStyle(
                "-fx-font-size:11.5px; -fx-font-family:'Monospaced';" +
                        "-fx-text-fill:#c8d0e8; -fx-padding:0;");
        roadInfoLabel.setWrapText(false);

        // DÃ²ng hÆ°á»›ng dáº«n â€” hiá»‡n khi hover, áº©n khi pin
        Label hintLabel = new Label("ðŸ–±  Chuá»™t pháº£i Ä‘á»ƒ ghim  â€¢  Click ngoÃ i Ä‘á»ƒ Ä‘Ã³ng");
        hintLabel.setStyle(
                "-fx-font-size:10px; -fx-text-fill:#4a6080; -fx-padding:2 0 0 0;");

        // NÃºt Sá»­a Ä‘Æ°á»ng nÃ y
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

        // NÃºt Sá»­a váº¡ch káº»
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
                previousMode = currentMode; // nhá»› mode Ä‘á»ƒ restore khi thoÃ¡t
                setInteractionType(InteractionType.EDIT_MARKINGS);
                if (onMarkingModeEntered != null) onMarkingModeEntered.accept(previousMode);
            }
        });

        // HBox chá»©a 2 nÃºt cáº¡nh nhau
        javafx.scene.layout.HBox btnRow = new javafx.scene.layout.HBox(8,
                roadInfoEditBtn, roadInfoEditMarkBtn);

        // hintLabel lÃ  index 1, btnRow lÃ  index 2
        VBox box = new VBox(6, roadInfoLabel, hintLabel, btnRow);
        box.setStyle(
                "-fx-background-color:#0d1420e8; -fx-border-color:#3a4a6a;" +
                        "-fx-border-width:1; -fx-padding:8 12 8 12;" +
                        "-fx-background-radius:6; -fx-border-radius:6;");

        roadInfoPopup.getContent().add(box);
        roadInfoPopup.setAutoHide(false);
        roadInfoPopup.setConsumeAutoHidingEvents(false);
    }

    /** Dá»±ng láº¡i ná»™i dung label tá»« segment. */
    private String buildRoadTipText(RoadSegment seg) {
        StringBuilder sb = new StringBuilder();
        if (seg instanceof HighwaySegment hwy) {
            sb.append("ðŸ›£  CAO Tá»C | ").append(seg.getLaneCount()).append(" lÃ n");
            sb.append("  â‰¥").append((int)hwy.getMinSpeedLimit()).append(" km/h");
            if (hwy.hasEmergencyLane()) sb.append("  ðŸŸ LÃ n KC");
        } else if (seg instanceof HighwayRampSegment ramp) {
            sb.append(ramp.getRampType() == HighwayRampSegment.RampType.ONRAMP
                    ? "â¤µ  VÃ€O CAO Tá»C" : "â¤´  RA CAO Tá»C");
            sb.append(" | ").append(seg.getLaneCount()).append(" lÃ n");
        } else {
            sb.append("ðŸ›£  ÄÆ°á»ng thÆ°á»ng | ").append(seg.getLaneCount()).append(" lÃ n");
        }
        sb.append(String.format("  |  %.0f m\n", seg.getLength() / 20.0 * 3.5));
        for (Lane l : seg.getLanes()) {
            sb.append(l.getDirection() == Lane.Direction.FORWARD ? "  â†’" : "  â†");
            sb.append(String.format(" %.1fm", l.getWidth()));
            if (l.getAllowedVehicles().contains(Lane.VehicleCategory.CAR))       sb.append(" ðŸš—");
            if (l.getAllowedVehicles().contains(Lane.VehicleCategory.MOTORBIKE)) sb.append("ðŸ›µ");
            if (l.getAllowedVehicles().contains(Lane.VehicleCategory.BUS))       sb.append("ðŸšŒ");
            if (l.getAllowedVehicles().contains(Lane.VehicleCategory.TRUCK))     sb.append("ðŸš›");
            if (l.getAllowedVehicles().contains(Lane.VehicleCategory.BICYCLE))   sb.append("ðŸš²");
            if (l.getAllowedVehicles().contains(Lane.VehicleCategory.EMERGENCY)) sb.append("ðŸš¨");
            sb.append("  [");
            if (l.getAllowedMovements().contains(Lane.Movement.STRAIGHT)) sb.append("â†‘");
            if (l.getAllowedMovements().contains(Lane.Movement.LEFT))     sb.append("â†°");
            if (l.getAllowedMovements().contains(Lane.Movement.RIGHT))    sb.append("â†±");
            if (l.getAllowedMovements().contains(Lane.Movement.U_TURN))  sb.append("â†©");
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
        // Hover: hint visible, hÃ ng nÃºt áº©n
        VBox box = (VBox) roadInfoPopup.getContent().get(0);
        box.getChildren().get(1).setVisible(true);   // hintLabel
        box.getChildren().get(1).setManaged(true);
        box.getChildren().get(2).setVisible(false);  // btnRow
        box.getChildren().get(2).setManaged(false);
        roadInfoEditBtn.setVisible(false);
        roadInfoEditBtn.setManaged(false);
        roadInfoEditMarkBtn.setVisible(false);
        roadInfoEditMarkBtn.setManaged(false);
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
        // Pin: áº©n hint, hiá»‡n hÃ ng nÃºt (cáº£ 2 nÃºt Sá»­a)
        VBox box = (VBox) roadInfoPopup.getContent().get(0);
        box.getChildren().get(1).setVisible(false);  // hintLabel
        box.getChildren().get(1).setManaged(false);
        box.getChildren().get(2).setVisible(true);   // btnRow
        box.getChildren().get(2).setManaged(true);
        roadInfoEditBtn.setVisible(true);
        roadInfoEditBtn.setManaged(true);
        roadInfoEditMarkBtn.setVisible(true);
        roadInfoEditMarkBtn.setManaged(true);
        // LuÃ´n show táº¡i vá»‹ trÃ­ má»›i â€” ká»ƒ cáº£ Ä‘ang hiá»‡n tá»« hover trÆ°á»›c Ä‘Ã³
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
        // áº¨n btnRow
        VBox box = (VBox) roadInfoPopup.getContent().get(0);
        if (box.getChildren().size() > 2) {
            box.getChildren().get(2).setVisible(false);
            box.getChildren().get(2).setManaged(false);
        }
        roadInfoPopup.hide();
    }
    // â”€â”€ Marking picker popup â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
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

        // XÃ¡c Ä‘á»‹nh mÃ u hiá»‡n táº¡i (vÃ ng hay tráº¯ng)
        boolean isYellow = (current == Lane.MarkingType.YELLOW_SOLID
                || current == Lane.MarkingType.YELLOW_DASHED
                || current == Lane.MarkingType.YELLOW_DOUBLE_SOLID
                || current == Lane.MarkingType.YELLOW_LEFT_DASHED_RIGHT_SOLID
                || current == Lane.MarkingType.YELLOW_LEFT_SOLID_RIGHT_DASHED);

        // â”€â”€ Danh sÃ¡ch kiá»ƒu váº¡ch (style â†’ white variant, yellow variant) â”€â”€
        record MarkEntry(String label, Lane.MarkingType white, Lane.MarkingType yellow) {}
        List<MarkEntry> entries = List.of(
                new MarkEntry("NÃ©t Ä‘á»©t",               Lane.MarkingType.DASHED,                     Lane.MarkingType.YELLOW_DASHED),
                new MarkEntry("NÃ©t liá»n",              Lane.MarkingType.SOLID,                      Lane.MarkingType.YELLOW_SOLID),
                new MarkEntry("NÃ©t Ä‘Ã´i",               Lane.MarkingType.DOUBLE_SOLID,               Lane.MarkingType.YELLOW_DOUBLE_SOLID),
                new MarkEntry("TrÃ¡i Ä‘á»©t / Pháº£i liá»n", Lane.MarkingType.LEFT_DASHED_RIGHT_SOLID,     Lane.MarkingType.YELLOW_LEFT_DASHED_RIGHT_SOLID),
                new MarkEntry("TrÃ¡i liá»n / Pháº£i Ä‘á»©t", Lane.MarkingType.LEFT_SOLID_RIGHT_DASHED,     Lane.MarkingType.YELLOW_LEFT_SOLID_RIGHT_DASHED),
                new MarkEntry("XÃ³a váº¡ch",              Lane.MarkingType.NONE,                       Lane.MarkingType.NONE)
        );

        // XÃ¡c Ä‘á»‹nh entry hiá»‡n táº¡i Ä‘ang chá»n
        MarkEntry currentEntry = entries.stream()
                .filter(en -> en.white() == current || en.yellow() == current)
                .findFirst().orElse(entries.get(0));

        // â”€â”€ Toggle mÃ u â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        javafx.beans.property.BooleanProperty yellowMode =
                new javafx.beans.property.SimpleBooleanProperty(isYellow);

        // â”€â”€ Build popup content â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        javafx.scene.layout.VBox root = new javafx.scene.layout.VBox(0);
        root.setStyle(
                "-fx-background-color:#16202e; -fx-border-color:#3a4a6a;" +
                        "-fx-border-width:1; -fx-background-radius:6; -fx-border-radius:6;");

        // Header: chá»n mÃ u tráº¯ng / vÃ ng
        javafx.scene.layout.HBox colorRow = new javafx.scene.layout.HBox(0);
        colorRow.setStyle("-fx-padding:6 8 6 8; -fx-border-color:transparent transparent #2a3a55 transparent; -fx-border-width:0 0 1 0;");
        Button btnWhite  = new Button("â¬œ Tráº¯ng");
        Button btnYellow = new Button("ðŸŸ¡ VÃ ng");
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

        // Danh sÃ¡ch váº¡ch
        for (MarkEntry entry : entries) {
            boolean isCurrent = (entry == currentEntry);
            Button btn = new Button((isCurrent ? "âœ”  " : "     ") + entry.label());
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
        // Cáº­p nháº­t selectedSegment sang object má»›i â€” trÃ¡nh stale reference
        // (Lane lÃ  immutable nÃªn replaceSegment táº¡o object hoÃ n toÃ n má»›i)
        if (selectedSegment == target.seg) selectedSegment = newSeg;
        hoveredBoundary = null;
        updateRenderData();
        hoveredBoundary = hitTestBoundary(
                toWorldX(target.screenX), toWorldY(target.screenY));
        redraw();
    }

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // INTERSECTION HIT-TEST HELPERS
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    private double[] getIntersectionPointStrict(double x1, double y1, double x2, double y2,
                                                double x3, double y3, double x4, double y4) {
        double denom = (x1-x2)*(y3-y4) - (y1-y2)*(x3-x4);
        if (denom == 0) return null;
        double t = ((x1-x3)*(y3-y4) - (y1-y3)*(x3-x4)) / denom;
        double u = -((x1-x2)*(y1-y3) - (y1-y2)*(x1-x3)) / denom;
        // YÃªu cáº§u giao Ä‘iá»ƒm pháº£i náº±m TRONG Ä‘oáº¡n (khÃ´ng tÃ­nh endpoint cá»§a Ä‘Æ°á»ng Ä‘ang váº½)
        // vÃ  náº±m TRONG Ä‘oáº¡n existing (khÃ´ng tÃ­nh endpoint â€” trÃ¡nh táº¡o intersection giáº£ khi kÃ©o dÃ i)
        boolean tInside = t > 0.01 && t < 0.99;   // Ä‘iá»ƒm trÃªn Ä‘Æ°á»ng Ä‘ang váº½ (khÃ´ng tÃ­nh 2 Ä‘áº§u)
        boolean uInside = u > 0.01 && u < 0.99;   // Ä‘iá»ƒm náº±m thá»±c sá»± giá»¯a Ä‘Æ°á»ng existing
        if (tInside && uInside)
            return new double[]{x1 + t*(x2-x1), y1 + t*(y2-y1)};
        // Cho phÃ©p t bao gá»“m Ä‘iá»ƒm Ä‘áº§u/cuá»‘i cá»§a Ä‘Æ°á»ng Ä‘ang váº½, nhÆ°ng u pháº£i lÃ  midpoint tháº­t sá»±
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

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // MOUSE EVENTS
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    private void setupEvents() {

        // Scroll = zoom táº¡i vá»‹ trÃ­ con trá»
        addEventHandler(ScrollEvent.SCROLL, e -> {
            double delta = e.getDeltaY() > 0 ? 1.1 : 0.9;
            double wx = toWorldX(e.getX()), wy = toWorldY(e.getY());
            scale *= delta;
            translateX = e.getX() - wx * scale;
            translateY = e.getY() - wy * scale;
            redraw();
        });

        // MOUSE_MOVED â€” hover highlight (chá»‰ EDIT mode, khÃ´ng kÃ©o)
        addEventHandler(MouseEvent.MOUSE_MOVED, e -> {
            double wx = toWorldX(e.getX()), wy = toWorldY(e.getY());
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
                // Popup dÃ¹ng vÃ¹ng rá»™ng hÆ¡n
                RoadSegment popupSeg = hitTestSegmentForPopup(wx, wy);
                if (popupSeg != null) showRoadTip(popupSeg, e.getScreenX(), e.getScreenY());
                else if (!tipPinned) hideRoadTip();
            } else {
                // PAN / DRAW mode: popup Ä‘i theo chuá»™t khi hover trÃªn Ä‘Æ°á»ng
                RoadSegment hovered = hitTestSegmentForPopup(wx, wy);
                if (hovered != null)
                    showRoadTip(hovered, e.getScreenX(), e.getScreenY());
                else if (!tipPinned)
                    hideRoadTip();
            }
        });

        // MOUSE_PRESSED
        addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            // Bá» qua press Ä‘áº§u tiÃªn sau khi popup Ä‘Ã³ng (trÃ¡nh pan/select khÃ´ng mong muá»‘n)
            if (ignoreNextPress) { ignoreNextPress = false; e.consume(); return; }
            if (e.getButton() == MouseButton.SECONDARY) {
                if (currentMode == InteractionType.EDIT_MARKINGS && hoveredBoundary != null) {
                    showMarkingMenu(hoveredBoundary, e.getScreenX(), e.getScreenY());
                } else if (currentMode == InteractionType.EDIT_MARKINGS) {
                    // Chuá»™t pháº£i ra ngoÃ i Ä‘Æ°á»ng trong EDIT_MARKINGS â†’ thoÃ¡t, restore mode cÅ©
                    double wxE = toWorldX(e.getX()), wyE = toWorldY(e.getY());
                    if (hitTestSegmentForPopup(wxE, wyE) == null) {
                        selectedSegment = null;
                        setInteractionType(previousMode);
                        if (onMarkingModeEntered != null) onMarkingModeEntered.accept(null);
                    }
                    hideActiveMenu();
                    lastMouseX = e.getX(); lastMouseY = e.getY();
                } else {
                    // Chuá»™t pháº£i trong Báº¤T Ká»² mode nÃ o: náº¿u trá» vÃ o Ä‘Æ°á»ng â†’ pin popup + nÃºt Sá»­a
                    double wxR = toWorldX(e.getX()), wyR = toWorldY(e.getY());
                    RoadSegment rightSeg = hitTestSegmentForPopup(wxR, wyR);
                    if (rightSeg != null) {
                        pinRoadTip(rightSeg, e.getScreenX(), e.getScreenY());
                    } else {
                        // Click chuá»™t pháº£i ra ngoÃ i Ä‘Æ°á»ng â†’ Ä‘Ã³ng popup náº¿u Ä‘ang pin
                        if (tipPinned) hideRoadTip();
                        hideActiveMenu();
                        lastMouseX = e.getX(); lastMouseY = e.getY();
                    }
                }
                return;
            }
            if (e.getButton() != MouseButton.PRIMARY) return;
            hideActiveMenu();

            // Click trÃ¡i ra ngoÃ i Ä‘Æ°á»ng â†’ áº©n popup náº¿u Ä‘ang pin
            double wx0 = toWorldX(e.getX()), wy0 = toWorldY(e.getY());
            if (tipPinned && hitTestSegmentForPopup(wx0, wy0) == null) hideRoadTip();

            // Ctrl+Click trong Báº¤T Ká»² mode nÃ o â†’ chá»n Ä‘Æ°á»ng Ä‘á»ƒ sá»­a
            if (e.isControlDown() && currentMode != InteractionType.DELETE) {
                RoadSegment clicked = hitTestSegment(wx0, wy0);
                selectedSegment = clicked;
                if (onSegmentSelected != null) onSegmentSelected.accept(clicked);
                redraw();
                if (clicked != null) return; // Ä‘á»«ng thá»±c hiá»‡n action mode
            }

            if (currentMode == InteractionType.PAN) {
                // Click trÃ¡i trong PAN mode: chá»n Ä‘Æ°á»ng Ä‘á»ƒ sá»­a (khÃ´ng chá»n arm stubs)
                double wx2 = toWorldX(e.getX()), wy2 = toWorldY(e.getY());
                RoadSegment clicked = hitTestSegment(wx2, wy2);
                if (clicked != null && clicked.isConnector()) clicked = null; // bá» qua stub
                if (clicked != null) {
                    selectedSegment = clicked;
                    redraw();
                    // ThÃ´ng bÃ¡o App cáº­p nháº­t nÃºt
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
                    saveSnapshot();
                    network.removeSegment(hoveredSegment);
                    hoveredSegment = null; updateRenderData();
                } else if (hoveredIntersection != null) {
                    saveSnapshot();
                    network.removeIntersection(hoveredIntersection);
                    hoveredIntersection = null; updateRenderData();
                }

            } else if (currentMode == InteractionType.PLACE_INTERSECTION) {
                double wx = toWorldX(e.getX()), wy = toWorldY(e.getY());
                // Snap vÃ o endpoint Ä‘Æ°á»ng gáº§n nháº¥t náº¿u cÃ³ (Ä‘á»ƒ Ä‘áº·t junction sÃ¡t Ä‘áº§u Ä‘Æ°á»ng)
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
                // Æ¯u tiÃªn 1: snap vÃ o endpoint cá»§a Ä‘Æ°á»ng hiá»‡n cÃ³ (khÃ´ng loáº¡i trá»« gÃ¬ á»Ÿ bÆ°á»›c press)
                double[] epSnap = snapToEndpoint(rawX, rawY, 60.0 / scale, Double.MAX_VALUE, Double.MAX_VALUE);
                if (epSnap != null) {
                    drawStartX = epSnap[0]; drawStartY = epSnap[1];
                    // Æ¯u tiÃªn 2: snap vÃ o tÃ¢m intersection
                } else {
                    Intersection mag = network.findNearestIntersection(rawX, rawY, 60.0 / scale);
                    if (mag != null) { drawStartX = mag.getCenterX(); drawStartY = mag.getCenterY(); }
                    else             { drawStartX = snap(rawX);        drawStartY = snap(rawY); }
                }
                drawCurrentX = drawStartX; drawCurrentY = drawStartY;
                isDrawing = true; redraw();
            } else if (currentMode == InteractionType.EDIT_MARKINGS) {
                // Click trÃ¡i ra ngoÃ i Ä‘Æ°á»ng â†’ thoÃ¡t, restore mode cÅ©
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
                // KhÃ´ng pan khi popup Ä‘ang pin â€” trÃ¡nh báº£n Ä‘á»“ trÃ´i lÃºc ngÆ°á»i dÃ¹ng giá»¯ chuá»™t pháº£i Ä‘á»ƒ Ä‘á»c
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
                // Náº¿u popup Ä‘ang pinned, di nÃ³ theo world (giá»¯ vá»‹ trÃ­ tÆ°Æ¡ng Ä‘á»‘i)
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
                    // Snap vÃ o intersection
                    Intersection mag = network.findNearestIntersection(rawX, rawY, 60.0 / scale);
                    if (mag != null) { drawCurrentX = mag.getCenterX(); drawCurrentY = mag.getCenterY(); }
                    else {
                        // Snap vÃ o Ä‘iá»ƒm báº¥t ká»³ trÃªn thÃ¢n Ä‘Æ°á»ng
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

            // Roundabout khÃ´ng cÃ²n Ä‘Æ°á»£c trigger tá»« sá»‘ lÃ n ná»¯a â€” dÃ¹ng mode PLACE_INTERSECTION

            List<Lane> newLanes = (laneFactory != null)
                    ? laneFactory.apply(currentLaneConfig)
                    : SimulationApp.createLanes(currentLaneConfig);

            if (autoIntersect) {
                commitWithIntersections(drawStartX, drawStartY, drawCurrentX, drawCurrentY, newLanes);
            } else {
                network.addSegment(makeSegment(drawStartX, drawStartY, drawCurrentX, drawCurrentY, newLanes));
                // Äá»“ng bá»™ arm stubs táº¡i 2 endpoint ngay cáº£ khi khÃ´ng autoIntersect
                syncStubLanesAt(drawStartX, drawStartY, newLanes);
                syncStubLanesAt(drawCurrentX, drawCurrentY, newLanes);
            }
            updateRenderData();
        });
    }

    /**
     * Táº¡o RoadSegment hoáº·c HighwaySegment tuá»³ theo highwayMode.
     */
    private RoadSegment makeSegment(double x1, double y1, double x2, double y2, List<Lane> lanes) {
        if (highwayMode) {
            int minLanes = emergencyLane ? 3 : 2;
            // Äáº£m báº£o Ä‘á»§ lÃ n â€” highway cáº§n tá»‘i thiá»ƒu 2 (hoáº·c 3 náº¿u cÃ³ lÃ n kháº©n cáº¥p)
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
     * Táº¡o Ä‘Æ°á»ng má»›i tá»« (x1,y1) Ä‘áº¿n (x2,y2), tá»± Ä‘á»™ng cáº¯t vÃ  táº¡o Intersection
     * táº¡i má»i Ä‘iá»ƒm giao vá»›i Ä‘Æ°á»ng hiá»‡n cÃ³.
     *
     * Quy táº¯c quan trá»ng:
     * - Náº¿u giao Ä‘iá»ƒm náº±m gáº§n má»™t Intersection cÃ³ sáºµn â†’ Ä‘Æ°á»ng má»›i Ä‘i QUA intersection Ä‘Ã³,
     *   KHÃ”NG táº¡o intersection má»›i, KHÃ”NG cáº¯t thÃªm cÃ¡c segment trong intersection Ä‘Ã³.
     * - Náº¿u giao Ä‘iá»ƒm náº±m giá»¯a má»™t RoadSegment thÃ´ng thÆ°á»ng â†’ cáº¯t Ä‘Ã´i, táº¡o intersection má»›i.
     */
    private void commitWithIntersections(double x1, double y1, double x2, double y2, List<Lane> newLanes) {
        List<RoadSegment> snapshot = new ArrayList<>(network.getSegments());

        // â”€â”€ BÆ°á»›c 1: Thu tháº­p hit points â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        // Má»—i "hit" lÃ  1 Ä‘iá»ƒm trÃªn Ä‘Æ°á»ng Ä‘ang váº½ mÃ  nÃ³ cáº¯t qua Ä‘Æ°á»ng existing.
        // Key insight: náº¿u Ä‘iá»ƒm cáº¯t náº±m gáº§n intersection cÃ³ sáºµn â†’ snap vÃ o tÃ¢m intersection Ä‘Ã³,
        // vÃ  Ä‘Ã¡nh dáº¥u lÃ  "existing intersection" (khÃ´ng pháº£i midpoint cut).

        // Map tá»« tá»a Ä‘á»™ â†’ HitGroup
        List<HitGroup> hits = new ArrayList<>();

        for (RoadSegment existing : snapshot) {
            double[] p = getIntersectionPointStrict(
                    x1, y1, x2, y2,
                    existing.getStartX(), existing.getStartY(),
                    existing.getEndX(),   existing.getEndY());
            if (p == null) continue;

            double cx = p[0], cy = p[1];

            // Bá» qua náº¿u trÃ¹ng Ä‘iá»ƒm báº¯t Ä‘áº§u/káº¿t thÃºc cá»§a Ä‘Æ°á»ng Ä‘ang váº½
            if (isSamePoint(cx, cy, x1, y1) || isSamePoint(cx, cy, x2, y2)) continue;

            // TÃ¬m intersection cÃ³ sáºµn gáº§n Ä‘iá»ƒm cáº¯t (snap radius lá»›n hÆ¡n: 60 world units)
            Intersection nearInter = network.findNearestIntersection(cx, cy, 60.0);
            boolean isExistingIntersection = (nearInter != null);
            if (isExistingIntersection) {
                cx = nearInter.getCenterX();
                cy = nearInter.getCenterY();
            }

            // Gom vÃ o HitGroup cÃ¹ng tá»a Ä‘á»™
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
            // Náº¿u lÃ  existing intersection thÃ¬ khÃ´ng cáº§n cáº¯t segment nÃ o
            if (!isExistingIntersection) {
                tgt.crossedSegments.add(existing);
            }
        }

        if (hits.isEmpty()) {
            network.addSegment(new RoadSegment(x1, y1, x2, y2, newLanes));
            return;
        }

        hits.sort((a, b) -> Double.compare(a.dist, b.dist));

        // â”€â”€ BÆ°á»›c 2: Táº¡o Ä‘Æ°á»ng theo tá»«ng hit â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        double curX = x1, curY = y1;
        GeneralIntersection lastNode = null;

        for (HitGroup group : hits) {
            if (isSamePoint(curX, curY, group.x, group.y)) continue;

            // Táº¡o Ä‘oáº¡n Ä‘Æ°á»ng tá»« vá»‹ trÃ­ hiá»‡n táº¡i Ä‘áº¿n Ä‘iá»ƒm giao
            RoadSegment inPart = new RoadSegment(curX, curY, group.x, group.y, newLanes);
            network.addSegment(inPart);
            if (lastNode != null) lastNode.connectRoad(inPart, ConnectionPoint.End.START);

            GeneralIntersection workNode;

            if (group.isExistingIntersection && group.existingIntersection != null) {
                // Äi qua intersection cÃ³ sáºµn â†’ má»Ÿ rá»™ng nÃ³ thÃªm 2 nhÃ¡nh (vÃ o/ra)
                Intersection old = group.existingIntersection;
                int newCap = Math.max(old.getRoadCount() + 2, 8);
                workNode = new GeneralIntersection(group.x, group.y, newCap);
                for (ConnectionPoint cp : old.getConnections())
                    workNode.connectRoad(cp.getSegment(), cp.getEnd());
                network.removeIntersection(old);
                network.addIntersection(workNode);
                // Cáº­p nháº­t tham chiáº¿u trong cÃ¡c hit tiáº¿p theo náº¿u chÃºng cÅ©ng snap vÃ o cÃ¹ng intersection
                for (HitGroup later : hits) {
                    if (later.existingIntersection == old) later.existingIntersection = null; // Ä‘Ã£ xá»­ lÃ½
                }
            } else {
                // Cáº¯t giá»¯a chá»«ng Ä‘Æ°á»ng existing â†’ táº¡o intersection má»›i
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

        // Äoáº¡n cuá»‘i
        if (!isSamePoint(curX, curY, x2, y2)) {
            RoadSegment tail = new RoadSegment(curX, curY, x2, y2, newLanes);
            network.addSegment(tail);
            if (lastNode != null) lastNode.connectRoad(tail, ConnectionPoint.End.START);
        }

        // â”€â”€ BÆ°á»›c 3: Äá»“ng bá»™ sá»‘ lÃ n arm stubs táº¡i 2 endpoint â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        // Náº¿u endpoint cá»§a Ä‘Æ°á»ng má»›i snap vÃ o Ä‘áº§u/cuá»‘i cá»§a arm stub (connector=true),
        // thÃ¬ replace stub Ä‘á»ƒ khá»›p sá»‘ lÃ n vá»›i Ä‘Æ°á»ng má»›i â†’ junction fill Ä‘Ãºng kÃ­ch thÆ°á»›c
        syncStubLanesAt(x1, y1, newLanes);
        syncStubLanesAt(x2, y2, newLanes);

        // â”€â”€ BÆ°á»›c 4: XÃ³a arm stubs Ä‘Ã£ Ä‘Æ°á»£c "thay tháº¿" bá»Ÿi Ä‘Æ°á»ng tháº­t â”€â”€â”€â”€â”€â”€â”€â”€â”€
        // Náº¿u endpoint cá»§a Ä‘Æ°á»ng má»›i trÃ¹ng vá»›i endpoint cá»§a má»™t stub â†’ stub Ä‘Ã³ khÃ´ng cÃ²n cáº§n ná»¯a
        removeStubsReplacedByRoadAt(x1, y1);
        removeStubsReplacedByRoadAt(x2, y2);
    }

    /**
     * XÃ³a arm stub (connector=true) cÃ³ endpoint Báº®T Äáº¦U táº¡i (wx,wy) â€”
     * nghÄ©a lÃ  Ä‘Æ°á»ng tháº­t Ä‘Ã£ "tháº¿ chá»—" stub Ä‘Ã³.
     * KhÃ´ng xÃ³a náº¿u táº¡i (wx,wy) khÃ´ng cÃ³ intersection (tá»©c stub chÆ°a Ä‘Æ°á»£c ná»‘i vÃ o giao lá»™ nÃ o).
     */
    private void removeStubsReplacedByRoadAt(double wx, double wy) {
        if (network == null) return;
        // Chá»‰ xÃ³a khi cÃ³ intersection tháº­t táº¡i Ä‘iá»ƒm nÃ y
        Intersection inter = network.findNearestIntersection(wx, wy, 60.0);
        if (inter == null) return;
        double icx = inter.getCenterX(), icy = inter.getCenterY();
        for (RoadSegment seg : new ArrayList<>(network.getSegments())) {
            if (!seg.isConnector()) continue;
            // Stub báº¯t Ä‘áº§u Tá»ª tÃ¢m giao lá»™ ra ngoÃ i â†’ Ä‘áº§u START lÃ  tÃ¢m giao lá»™
            boolean startAtCenter = isSamePoint(seg.getStartX(), seg.getStartY(), icx, icy);
            boolean endAtCenter   = isSamePoint(seg.getEndX(),   seg.getEndY(),   icx, icy);
            if (!startAtCenter && !endAtCenter) continue;
            // Kiá»ƒm tra Ä‘áº§u ngoÃ i (tip) cá»§a stub â€” náº¿u cÃ³ Ä‘Æ°á»ng tháº­t ná»‘i vÃ o Ä‘Ã³ thÃ¬ xÃ³a stub
            double tipX = startAtCenter ? seg.getEndX()   : seg.getStartX();
            double tipY = startAtCenter ? seg.getEndY()   : seg.getStartY();
            // ÄÆ°á»ng tháº­t = khÃ´ng pháº£i connector, cÃ³ endpoint trÃ¹ng vá»›i tip cá»§a stub
            boolean hasRealRoad = network.getSegments().stream()
                    .anyMatch(s -> !s.isConnector() && s != seg &&
                            (isSamePoint(s.getStartX(), s.getStartY(), tipX, tipY) ||
                                    isSamePoint(s.getEndX(),   s.getEndY(),   tipX, tipY)));
            if (hasRealRoad) network.removeSegment(seg);
        }
    }

    /**
     * Náº¿u táº¡i (wx, wy) cÃ³ arm stub (connector=true), replace nÃ³ báº±ng báº£n má»›i cÃ³ sá»‘ lÃ n = newLanes.
     * Intersection radius sáº½ tá»± tÃ­nh láº¡i tá»« sá»‘ lÃ n má»›i qua getRenderData().
     */
    private void syncStubLanesAt(double wx, double wy, List<Lane> newLanes) {
        // Sync stubs cÃ³ endpoint táº¡i (wx, wy)
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
        // Náº¿u endpoint gáº§n tÃ¢m intersection â†’ sync Táº¤T Cáº¢ stubs cá»§a intersection Ä‘Ã³
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
     * Táº¡o danh sÃ¡ch lÃ n má»›i: giá»¯ pattern chiá»u (FWD/BWD) tá»« oldLanes, Ä‘á»•i sá»‘ lÆ°á»£ng theo newLanes.
     * DÃ¹ng thÃ´ng tin width + vehicle + movement tá»« newLanes lÃ m template.
     */
    private List<Lane> rebuildLanesMatchingCount(List<Lane> oldLanes, List<Lane> newLanes) {
        List<Lane> result = new ArrayList<>();
        int n = newLanes.size();
        for (int i = 0; i < n; i++) {
            Lane template = newLanes.get(i);
            // Giá»¯ chiá»u Ä‘Æ°á»ng tá»« oldLanes náº¿u cÃ³, khÃ´ng thÃ¬ dÃ¹ng template
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
     * Láº¥y Intersection hiá»‡n cÃ³ táº¡i (cx, cy) hoáº·c táº¡o má»›i.
     * capacity = sá»‘ Ä‘Æ°á»ng existing bá»‹ cáº¯t + 2 (Ä‘Æ°á»ng má»›i vÃ o vÃ  ra).
     * LuÃ´n dÃ¹ng capacity Ä‘á»§ lá»›n (tá»‘i thiá»ƒu 8) Ä‘á»ƒ trÃ¡nh lá»—i "vÆ°á»£t quÃ¡ sá»‘ Ä‘Æ°á»ng".
     */
    private GeneralIntersection getOrCreateIntersection(double cx, double cy, int crossedCount) {
        Intersection existNode = network.findNearestIntersection(cx, cy, 5.0);
        if (existNode != null) {
            int oldCount = existNode.getRoadCount();
            // capacity = sá»‘ nhÃ¡nh cÅ© + 2 nhÃ¡nh má»›i (vÃ o/ra) + sá»‘ Ä‘Æ°á»ng bá»‹ cáº¯t thÃªm * 2
            int newCap = Math.max(oldCount + crossedCount * 2 + 2, 8);
            GeneralIntersection workNode = new GeneralIntersection(cx, cy, newCap);
            for (ConnectionPoint cp : existNode.getConnections())
                workNode.connectRoad(cp.getSegment(), cp.getEnd());
            network.removeIntersection(existNode);
            network.addIntersection(workNode);
            return workNode;
        } else {
            // capacity Ä‘á»§ lá»›n cho má»i trÆ°á»ng há»£p
            int cap = Math.max(crossedCount * 2 + 2, 8);
            GeneralIntersection workNode = new GeneralIntersection(cx, cy, cap);
            network.addIntersection(workNode);
            return workNode;
        }
    }

    /**
     * Äáº·t vÃ²ng xuyáº¿n khi dÃ¹ng DRAW_ROAD vá»›i laneConfig > 8.
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
     * Äáº·t giao lá»™ táº¡i tá»a Ä‘á»™ world (wx, wy) theo loáº¡i Ä‘ang Ä‘Æ°á»£c chá»n.
     * Guard radius nhá» (25 world units) â€” khÃ´ng phá»¥ thuá»™c scale.
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

        // Arm stubs: chiá»u rá»™ng 1 lÃ n = 20 world units â†’ 5 lÃ n = 100 world units half
        // Stub pháº£i Ä‘á»§ dÃ i Ä‘á»ƒ junction radius (minArmLen * 0.4) > halfWidth Ä‘Æ°á»ng
        // halfWidth = laneCount * 20 / 2. Cáº§n: stubLen * 0.4 > halfWidth â†’ stubLen > halfWidth * 2.5
        int laneCount = currentLaneConfig;
        double halfWidth = laneCount * 20.0 / 2.0;
        double stubLen  = Math.max(150.0, halfWidth * 3.0); // Ä‘á»§ dÃ i cho má»i Ä‘á»™ rá»™ng

        List<Lane> stubLanes = (laneFactory != null)
                ? laneFactory.apply(laneCount)
                : SimulationApp.createLanes(laneCount);

        for (int i = 0; i < armCount; i++) {
            // GÃ³c thá»±c táº¿ theo tá»«ng loáº¡i giao lá»™:
            // NgÃ£ 3: Báº¯c + TÃ¢y-Nam + ÄÃ´ng-Nam (hÃ¬nh chá»¯ T ngÆ°á»£c)
            // NgÃ£ 4: Báº¯c ÄÃ´ng Nam TÃ¢y
            // NgÃ£ 5: Báº¯c + TÃ¢y-Báº¯c + ÄÃ´ng-Báº¯c + TÃ¢y-Nam + ÄÃ´ng-Nam
            // NgÃ£ 6: Ä‘á»u 60Â°
            double angleDeg;
            if (armCount == 3) {
                double[] angles3 = {-90, 150, 30}; // Báº¯c, TÃ¢y-Nam, ÄÃ´ng-Nam (chá»¯ Y)
                angleDeg = angles3[i];
            } else if (armCount == 5) {
                double[] angles5 = {-90, -162, -18, 126, 54}; // Báº¯c + 4 nhÃ¡nh xÃ²e ra
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

    public void setController(com.myteam.traffic.controller.TrafficController controller) {
        this.controller = controller;
    }
}