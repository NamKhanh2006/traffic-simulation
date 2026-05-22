package com.myteam.traffic.ui;

import com.myteam.traffic.model.infrastructure.*;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * SimulationApp — Entry point với UI 2 tầng mở rộng được.
 * Phiên bản mở rộng: thêm tuỳ chỉnh chiều rộng làn, chiều đường,
 * loại xe cho phép, bán kính vòng xuyến.
 */
public class SimulationApp extends Application {

    // ── Cấu hình làn mặc định ────────────────────────────────
    private SimulationView view; // field để dialog methods dùng được
    private double currentLaneWidth  = 3.5;
    private boolean onewayMode       = false;
    private Set<Lane.VehicleCategory> allowedVehicles =
            EnumSet.of(Lane.VehicleCategory.CAR, Lane.VehicleCategory.MOTORBIKE,
                    Lane.VehicleCategory.BUS);
    private Set<Lane.Movement> currentMovements =
            EnumSet.of(Lane.Movement.STRAIGHT, Lane.Movement.LEFT, Lane.Movement.RIGHT);
    private double roundaboutRadius  = 60.0;

    // ── Lane factories ────────────────────────────────────────
    private Lane fwdLane(int idx) {
        return new Lane(idx, Lane.Direction.FORWARD, currentLaneWidth,
                allowedVehicles, currentMovements,
                Lane.MarkingType.DASHED, Lane.MarkingType.DASHED);
    }

    private Lane bwdLane(int idx) {
        // Làn ngược chiều: hướng rẽ là đối xứng (STRAIGHT + đảo LEFT/RIGHT)
        Set<Lane.Movement> bwdMov = EnumSet.noneOf(Lane.Movement.class);
        if (currentMovements.contains(Lane.Movement.STRAIGHT)) bwdMov.add(Lane.Movement.STRAIGHT);
        if (currentMovements.contains(Lane.Movement.LEFT))     bwdMov.add(Lane.Movement.RIGHT);
        if (currentMovements.contains(Lane.Movement.RIGHT))    bwdMov.add(Lane.Movement.LEFT);
        if (currentMovements.contains(Lane.Movement.U_TURN))   bwdMov.add(Lane.Movement.U_TURN);
        if (bwdMov.isEmpty()) bwdMov.add(Lane.Movement.STRAIGHT);
        return new Lane(idx, Lane.Direction.BACKWARD, currentLaneWidth,
                allowedVehicles, bwdMov,
                Lane.MarkingType.DASHED, Lane.MarkingType.DASHED);
    }

    /**
     * Static fallback — dùng khi laneFactory chưa được set (chiều rộng 3.5m mặc định).
     */
    public static List<Lane> createLanes(int n) {
        List<Lane> l = new ArrayList<>();
        int fwd = (n + 1) / 2, bwd = n / 2;
        for (int i = 0; i < fwd; i++) l.add(new Lane(i, Lane.Direction.FORWARD, 3.5,
                EnumSet.of(Lane.VehicleCategory.CAR, Lane.VehicleCategory.MOTORBIKE),
                EnumSet.of(Lane.Movement.STRAIGHT), Lane.MarkingType.DASHED, Lane.MarkingType.DASHED));
        for (int i = 0; i < bwd; i++) l.add(new Lane(fwd + i, Lane.Direction.BACKWARD, 3.5,
                EnumSet.of(Lane.VehicleCategory.CAR, Lane.VehicleCategory.BUS),
                EnumSet.of(Lane.Movement.STRAIGHT), Lane.MarkingType.DASHED, Lane.MarkingType.DASHED));
        return l;
    }

    /**
     * Instance version — dùng cấu hình hiện tại (chiều rộng, loại xe, chiều đường).
     * Được truyền vào SimulationView qua setLaneFactory(this::createLanesInstance).
     */
    public List<Lane> createLanesInstance(int totalLanes) {
        List<Lane> lanes = new ArrayList<>();
        if (totalLanes == 1 || onewayMode) {
            for (int i = 0; i < totalLanes; i++) lanes.add(fwdLane(i));
            return lanes;
        }
        int fwd = (totalLanes + 1) / 2;
        int bwd = totalLanes / 2;
        for (int i = 0; i < fwd; i++) lanes.add(fwdLane(i));
        for (int i = 0; i < bwd; i++) lanes.add(bwdLane(fwd + i));
        return lanes;
    }

    // ── Start ─────────────────────────────────────────────────
    @Override
    public void start(Stage stage) {
        SimulationView view = new SimulationView(1200, 750);
        this.view = view;
        RoadNetwork network = new RoadNetwork();
        view.setNetwork(network);
        view.setLaneFactory(this::createLanesInstance);

        // ════ TOP BAR ════════════════════════════════════════
        ToggleButton btnModePan    = modeBtn("🖐", "Di chuyển",  "Kéo và xoay bản đồ");
        ToggleButton btnModeDraw   = modeBtn("🛣", "Vẽ đường",   "Vẽ đoạn đường mới");
        ToggleButton btnModeInter  = modeBtn("🔀", "Giao lộ",    "Click để đặt nút giao thông");
        ToggleButton btnModeDelete = modeBtn("🗑", "Xóa",        "Hover vào đường/nút giao → click để xóa");

        // Nút xóa màu đỏ khi active
        btnModeDelete.selectedProperty().addListener((o, was, is) -> {
            String base = "-fx-font-size:13px; -fx-font-weight:bold; -fx-cursor:hand; " +
                    "-fx-pref-height:32px; -fx-padding:3 14 3 14; -fx-background-radius:6;";
            btnModeDelete.setStyle(base + (is
                    ? "-fx-background-color:#c0392b; -fx-text-fill:white;"
                    : "-fx-background-color:#2e3d52; -fx-text-fill:#c8d0e8;"));
        });

        ToggleGroup modeGroup = new ToggleGroup();
        for (ToggleButton b : new ToggleButton[]{btnModePan, btnModeDraw, btnModeInter, btnModeDelete})
            b.setToggleGroup(modeGroup);
        btnModePan.setSelected(true);

        HBox modeBox = new HBox(4, btnModePan, btnModeDraw, btnModeInter, btnModeDelete);
        modeBox.setAlignment(Pos.CENTER_LEFT);

        Button btnZoomIn  = actionBtn("🔍+", "Phóng to  (Scroll)");
        Button btnZoomOut = actionBtn("🔎-", "Thu nhỏ  (Scroll)");
        Button btnUndo    = actionBtn("↩ Hoàn tác", "Ctrl + Z");
        Button btnClear   = actionBtn("🗑 Xóa sạch", "Xóa toàn bộ");

        btnZoomIn .setOnAction(e -> view.zoomIn());
        btnZoomOut.setOnAction(e -> view.zoomOut());
        btnUndo   .setOnAction(e -> view.undo());
        btnClear  .setOnAction(e -> { view.saveSnapshot(); view.setNetwork(new RoadNetwork()); });

        CheckBox cbGrid   = styledCb("Lưới",       true,  e -> view.setShowGrid  (((CheckBox)e.getSource()).isSelected()));
        CheckBox cbLabels = styledCb("Nhãn đường", true,  e -> view.setShowLabels(((CheckBox)e.getSource()).isSelected()));

        Label hint = new Label("🖱 TRÁI: Thao tác   PHẢI: Kéo bản đồ   SCROLL: Zoom");
        hint.setStyle("-fx-text-fill:#e8d44d; -fx-font-size:11px; -fx-padding:0 0 0 8;");

        HBox topBar = new HBox(10,
                modeBox,
                vsep(), new HBox(4, btnZoomIn, btnZoomOut, btnUndo, btnClear),
                vsep(), new HBox(10, cbGrid, cbLabels),
                vsep(), hint);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(7, 14, 7, 14));
        topBar.setStyle("-fx-background-color:#141824;");

        // ════ CONTEXT BAR ════════════════════════════════════
        HBox ctxPan    = buildCtxPan();
        HBox ctxDraw   = buildCtxDraw(view);
        HBox ctxInter  = buildCtxIntersection(view);
        HBox ctxDelete = buildCtxDelete();

        StackPane ctxStack = new StackPane(ctxPan, ctxDraw, ctxInter, ctxDelete);
        ctxPan.setVisible(true);
        for (HBox h : new HBox[]{ctxDraw, ctxInter, ctxDelete}) {
            h.setVisible(false);
            StackPane.setAlignment(h, Pos.CENTER_LEFT);
        }
        StackPane.setAlignment(ctxPan, Pos.CENTER_LEFT);
        ctxStack.setPadding(new Insets(5, 14, 5, 14));
        ctxStack.setStyle("-fx-background-color:#1a2030; -fx-border-color:#252f45; -fx-border-width:1 0 0 0;");
        ctxStack.setMinHeight(38);

        modeGroup.selectedToggleProperty().addListener((obs, old, nw) -> {
            ctxPan   .setVisible(nw == btnModePan);
            ctxDraw  .setVisible(nw == btnModeDraw);
            ctxInter .setVisible(nw == btnModeInter);
            ctxDelete.setVisible(nw == btnModeDelete);
            if (nw == btnModePan)    view.setInteractionType(SimulationView.InteractionType.PAN);
            if (nw == btnModeDraw)   view.setInteractionType(SimulationView.InteractionType.DRAW_ROAD);
            if (nw == btnModeInter)  view.setInteractionType(SimulationView.InteractionType.PLACE_INTERSECTION);
            if (nw == btnModeDelete) view.setInteractionType(SimulationView.InteractionType.DELETE);
        });

        // Khi view thay đổi trạng thái EDIT_MARKINGS:
        // prevMode != null → vừa vào EDIT_MARKINGS, toolbar về PAN (không có nút EDIT_MARKINGS), runLater restore mode thật
        // prevMode == null → vừa thoát, toolbar restore về mode trước đó (prevMode đã được set trong view)
        view.setOnMarkingModeEntered(prevMode -> {
            if (prevMode != null) {
                // Đang vào EDIT_MARKINGS: toolbar về PAN tạm, rồi view tự giữ EDIT_MARKINGS
                modeGroup.selectToggle(btnModePan);
                javafx.application.Platform.runLater(() ->
                        view.setInteractionType(SimulationView.InteractionType.EDIT_MARKINGS));
            } else {
                // Thoát EDIT_MARKINGS: view đã restore previousMode, sync toolbar
                SimulationView.InteractionType cur = view.getCurrentMode();
                if      (cur == SimulationView.InteractionType.DRAW_ROAD)          modeGroup.selectToggle(btnModeDraw);
                else if (cur == SimulationView.InteractionType.PLACE_INTERSECTION) modeGroup.selectToggle(btnModeInter);
                else if (cur == SimulationView.InteractionType.DELETE)             modeGroup.selectToggle(btnModeDelete);
                else                                                               modeGroup.selectToggle(btnModePan);
            }
        });

        // ════ LAYOUT ═════════════════════════════════════════
        VBox header = new VBox(topBar, ctxStack);
        StackPane canvasHolder = new StackPane(view);
        canvasHolder.setStyle("-fx-background-color:#1a1f2e;");
        view.widthProperty() .bind(canvasHolder.widthProperty());
        view.heightProperty().bind(canvasHolder.heightProperty());

        BorderPane root = new BorderPane();
        root.setTop(header);
        root.setCenter(canvasHolder);

        Scene scene = new Scene(root, 1280, 850);
        scene.getStylesheets().add("data:text/css," +
                ".check-box .text{-fx-fill:#c8d0e8;}" +
                ".label{-fx-text-fill:#c8d0e8;}" +
                ".slider .thumb{-fx-background-color:#e8d44d;}" +
                ".slider .track{-fx-background-color:#2e3d52;}" +
                ".combo-box-popup .list-view{-fx-background-color:#1a2030;-fx-border-color:#3a4560;}" +
                ".combo-box-popup .list-cell{-fx-text-fill:#c8d0e8;-fx-padding:4 8 4 8;}" +
                ".combo-box-popup .list-cell:hover{-fx-background-color:#3d4a5c;}");
        scene.setOnKeyPressed(e -> { if (e.isControlDown() && e.getCode() == KeyCode.Z) view.undo(); });

        stage.setTitle("Traffic Builder — Trình Dựng Lưới Giao Thông");
        stage.setScene(scene);
        stage.show();
        view.resetView();
    }

    // ════════════════════════════════════════════════════════
    // CONTEXT BARS
    // ════════════════════════════════════════════════════════

    private HBox buildCtxPan() {
        Label lbl = new Label("🖐  Giữ chuột PHẢI để kéo bản đồ  •  Scroll để zoom.");
        lbl.setStyle("-fx-text-fill:#5a7090; -fx-font-size:11px;");
        return hbox(lbl);
    }

    private HBox buildCtxDraw(SimulationView view) {
        // ── Số làn — Spinner ─────────────────────────────────────
        Label lblLane = ctxLabel("Số làn:");
        Spinner<Integer> spinLane = new Spinner<>(1, 16, 4);
        spinLane.setEditable(true);
        spinLane.setPrefWidth(72);
        spinLane.setStyle("-fx-background-color:#2a3550; -fx-background-radius:4; -fx-border-color:#3a4560; -fx-border-radius:4;");
        spinLane.getEditor().setStyle("-fx-background-color:#2a3550; -fx-text-fill:#c8d0e8; -fx-font-weight:bold; -fx-font-size:12px; -fx-alignment:center;");
        spinLane.getEditor().setAlignment(javafx.geometry.Pos.CENTER);
        view.setCurrentLaneConfig(4);
        // Commit khi Enter hoặc focus out
        spinLane.getEditor().setOnAction(e -> {
            try {
                int v = Math.max(1, Math.min(16, Integer.parseInt(spinLane.getEditor().getText().trim())));
                spinLane.getValueFactory().setValue(v);
                view.setCurrentLaneConfig(v);
            } catch (NumberFormatException ex) { spinLane.getEditor().setText(spinLane.getValue().toString()); }
            // Blur focus → con trỏ biến mất, người dùng thấy đã set xong
            spinLane.getParent().requestFocus();
        });
        spinLane.getEditor().focusedProperty().addListener((ob, ov, focused) -> {
            if (!focused) spinLane.getEditor().getOnAction().handle(null);
        });
        spinLane.valueProperty().addListener((ob, ov, nv) -> { if (nv != null) view.setCurrentLaneConfig(nv); });

        // ── Chiều rộng làn: Slider + TextField ──────────────────
        Label lblW = ctxLabel("Rộng làn:");
        Slider sliderW = new Slider(2.5, 6.0, 3.5);
        sliderW.setPrefWidth(70);
        sliderW.setShowTickMarks(false);
        TextField tfW = new TextField("3.5");
        tfW.setPrefWidth(46);
        tfW.setStyle("-fx-background-color:#2a3550; -fx-text-fill:#e8d44d; -fx-font-size:12px; -fx-border-color:#3a4560;");
        Label lblWUnit = ctxLabel("m");
        sliderW.valueProperty().addListener((ob, ov, nv) -> {
            currentLaneWidth = Math.round(nv.doubleValue() * 10.0) / 10.0;
            tfW.setText(String.format("%.1f", currentLaneWidth));
        });
        Runnable applyWidth = () -> {
            try {
                double v = Math.max(2.0, Math.min(6.0, Double.parseDouble(tfW.getText().trim())));
                currentLaneWidth = Math.round(v * 10.0) / 10.0;
                sliderW.setValue(currentLaneWidth);
                tfW.setText(String.format("%.1f", currentLaneWidth));
            } catch (NumberFormatException ex) { tfW.setText(String.format("%.1f", currentLaneWidth)); }
        };
        tfW.setOnAction(e -> applyWidth.run());
        tfW.focusedProperty().addListener((ob, ov, focused) -> { if (!focused) applyWidth.run(); });

        // ── Chiều đường ──────────────────────────────────────────
        Label lblDir = ctxLabel("Chiều:");
        ToggleButton btnTwo = smallToggle("⇄ Hai chiều", true);
        ToggleButton btnOne = smallToggle("→ Một chiều", false);
        ToggleGroup dirGroup = new ToggleGroup();
        btnTwo.setToggleGroup(dirGroup); btnOne.setToggleGroup(dirGroup);
        btnTwo.setSelected(true);
        dirGroup.selectedToggleProperty().addListener((ob, ov, nv) -> onewayMode = (nv == btnOne));

        // Callback: khi nhấn nút "✏ Sửa" trong popup info đường
        view.setOnSegmentSelected(seg -> {
            if (seg == null) return;
            showLaneConfigDialog(seg.getLaneCount(), seg);
            view.clearSelectedSegment();
        });

        // ── Cao tốc ───────────────────────────────────────────────
        Label lblHwy = ctxLabel("Cao tốc:");
        CheckBox cbHwy = new CheckBox("Bật");
        cbHwy.setTextFill(Color.web("#6bc5ff")); cbHwy.setStyle("-fx-font-size:11px;");
        Label lblSpeed = ctxLabel("Tốc độ min:");
        TextField tfSpeed = new TextField("60");
        tfSpeed.setPrefWidth(44);
        tfSpeed.setStyle("-fx-background-color:#2a3550; -fx-text-fill:#e8d44d; -fx-font-size:12px; -fx-border-color:#3a4560;");
        Label lblSpeedU = ctxLabel("km/h");
        for (javafx.scene.Node n : new javafx.scene.Node[]{lblSpeed, tfSpeed, lblSpeedU}) n.setVisible(false);
        cbHwy.setOnAction(e -> {
            boolean on = cbHwy.isSelected();
            view.setHighwayMode(on);
            for (javafx.scene.Node n : new javafx.scene.Node[]{lblSpeed, tfSpeed, lblSpeedU}) n.setVisible(on);
        });
        tfSpeed.setOnAction(e -> {
            try { view.setHighwayMinSpeed(Double.parseDouble(tfSpeed.getText().trim())); }
            catch (NumberFormatException ex) {}
            tfSpeed.getParent().requestFocus();
        });
        tfSpeed.focusedProperty().addListener((ob, ov, focused) -> {
            if (!focused) { try { view.setHighwayMinSpeed(Double.parseDouble(tfSpeed.getText().trim())); } catch (NumberFormatException ignored) {} }
        });
        // Làn khẩn cấp được cấu hình bên trong dialog "Sửa đường này"

        // ── Auto-intersect ────────────────────────────────────────
        CheckBox cbAuto = styledCb("Tự tạo nút giao", true,
                e2 -> view.setAutoIntersect(((CheckBox)e2.getSource()).isSelected()));

        HBox box = hbox(
                lblLane, spinLane,
                vsep(), lblW, sliderW, tfW, lblWUnit,
                vsep(), lblDir, btnTwo, btnOne,
                vsep(), lblHwy, cbHwy, lblSpeed, tfSpeed, lblSpeedU,
                vsep(), cbAuto);
        box.setSpacing(6);
        return box;
    }

    /**
     * Popup cấu hình chi tiết từng làn: loại xe + hướng rẽ riêng cho làn xuôi/ngược.
     */
    private void showLaneConfigDialog(int laneCount, RoadSegment targetSeg) {
        Stage dlg = new Stage();
        dlg.setTitle(targetSeg != null
                ? "Sửa chi tiết làn — đường đang hover"
                : "Cấu hình chi tiết làn — áp dụng cho đường vẽ tiếp");
        dlg.initModality(javafx.stage.Modality.APPLICATION_MODAL);

        String DARK = "-fx-background-color:#1a2030;";
        String CELL = "-fx-background-color:#212d3a; -fx-border-color:#2a3550; -fx-border-width:0 0 1 0; -fx-padding:6 10 6 10;";

        VBox root = new VBox(10);
        root.setStyle(DARK + " -fx-padding:16;");

        Label title = new Label("Cấu hình " + laneCount + " làn — áp dụng cho đường vẽ tiếp theo");
        title.setStyle("-fx-text-fill:#e8d44d; -fx-font-size:13px; -fx-font-weight:bold;");
        root.getChildren().add(title);

        // Header row
        HBox header = new HBox(0);
        for (String h : new String[]{"Làn", "Chiều", "Loại xe cho phép", "Hướng được phép đi"}) {
            Label lh = new Label(h);
            lh.setStyle("-fx-text-fill:#8090b0; -fx-font-size:11px; -fx-font-weight:bold; -fx-padding:4 10 4 10;");
            lh.setPrefWidth(h.equals("Làn") || h.equals("Chiều") ? 70 : 260);
            header.getChildren().add(lh);
        }
        header.setStyle("-fx-background-color:#141824; -fx-border-color:#2a3550; -fx-border-width:0 0 1 0;");
        root.getChildren().add(header);

        // Per-lane rows
        int fwd = onewayMode ? laneCount : (laneCount + 1) / 2;
        int bwd = onewayMode ? 0 : laneCount / 2;

        // Cấu hình riêng per-lane (dùng lambda-captured arrays)
        @SuppressWarnings("unchecked")
        Set<Lane.VehicleCategory>[] vehPerLane = new Set[laneCount];
        @SuppressWarnings("unchecked")
        Set<Lane.Movement>[] movPerLane = new Set[laneCount];
        for (int i = 0; i < laneCount; i++) {
            if (targetSeg != null && i < targetSeg.getLanes().size()) {
                Lane l = targetSeg.getLanes().get(i);
                vehPerLane[i] = l.getAllowedVehicles().isEmpty()
                        ? EnumSet.of(Lane.VehicleCategory.CAR)
                        : EnumSet.copyOf(l.getAllowedVehicles());
                movPerLane[i] = l.getAllowedMovements().isEmpty()
                        ? EnumSet.of(Lane.Movement.STRAIGHT)
                        : EnumSet.copyOf(l.getAllowedMovements());
            } else {
                vehPerLane[i] = EnumSet.copyOf(allowedVehicles);
                movPerLane[i] = EnumSet.copyOf(currentMovements);
            }
        }

        ScrollPane scroll = new ScrollPane();
        VBox laneRows = new VBox(1);
        laneRows.setStyle(DARK);

        for (int i = 0; i < laneCount; i++) {
            final int idx = i;
            boolean isFwd = i < fwd;
            String dirLabel = isFwd ? "→ Xuôi" : "← Ngược";
            Color dirColor  = isFwd ? Color.web("#6bc5ff") : Color.web("#ff9966");

            HBox row = new HBox(0);
            row.setStyle(CELL);
            row.setAlignment(Pos.CENTER_LEFT);

            // Làn số
            Label lLane = new Label("Làn " + (i + 1));
            lLane.setStyle("-fx-text-fill:#c8d0e8; -fx-font-size:12px; -fx-font-weight:bold; -fx-min-width:70px;");

            // Chiều
            Label lDir = new Label(dirLabel);
            lDir.setTextFill(dirColor);
            lDir.setStyle("-fx-font-size:11px; -fx-min-width:70px;");

            // Loại xe (checkboxes nhỏ + tooltip)
            HBox vehBox = new HBox(6);
            vehBox.setPrefWidth(260); vehBox.setAlignment(Pos.CENTER_LEFT);
            record VehDef(String icon, String tip, Lane.VehicleCategory cat) {}
            var vehDefs = new VehDef[]{
                    new VehDef("🚗", "Ô tô",          Lane.VehicleCategory.CAR),
                    new VehDef("🛵", "Xe máy",         Lane.VehicleCategory.MOTORBIKE),
                    new VehDef("🚌", "Xe buýt",        Lane.VehicleCategory.BUS),
                    new VehDef("🚛", "Xe tải nặng",    Lane.VehicleCategory.TRUCK),
                    new VehDef("🚲", "Xe đạp",         Lane.VehicleCategory.BICYCLE),
                    new VehDef("🚨", "Xe khẩn cấp",   Lane.VehicleCategory.EMERGENCY),
            };
            for (var def : vehDefs) {
                CheckBox cb = miniCb(def.icon(), vehPerLane[idx].contains(def.cat()),
                        c -> { if(c) vehPerLane[idx].add(def.cat()); else vehPerLane[idx].remove(def.cat()); });
                Tooltip tt = new Tooltip(def.tip());
                tt.setStyle("-fx-font-size:12px;");
                Tooltip.install(cb, tt);
                vehBox.getChildren().add(cb);
            }

            // Hướng được phép đi (checkboxes nhỏ + tooltip)
            HBox movBox = new HBox(6);
            movBox.setPrefWidth(250); movBox.setAlignment(Pos.CENTER_LEFT);
            Set<Lane.Movement> mvSet = movPerLane[idx];
            record MovDef(String icon, String tip, Lane.Movement mov) {}
            var movDefs = isFwd
                    ? new MovDef[]{
                    new MovDef("↑ Thẳng",   "Đi thẳng",       Lane.Movement.STRAIGHT),
                    new MovDef("↰ Trái",    "Rẽ trái",        Lane.Movement.LEFT),
                    new MovDef("↱ Phải",    "Rẽ phải",        Lane.Movement.RIGHT),
                    new MovDef("↩ Quay đầu","Quay đầu xe",    Lane.Movement.U_TURN),
            }
                    : new MovDef[]{
                    new MovDef("↑ Thẳng",   "Đi thẳng",       Lane.Movement.STRAIGHT),
                    new MovDef("↱ Phải",    "Rẽ phải (theo chiều ngược)", Lane.Movement.LEFT),
                    new MovDef("↰ Trái",    "Rẽ trái (theo chiều ngược)", Lane.Movement.RIGHT),
                    new MovDef("↩ Quay đầu","Quay đầu xe",    Lane.Movement.U_TURN),
            };
            for (var def : movDefs) {
                CheckBox cb = miniCb(def.icon(), mvSet.contains(def.mov()),
                        c -> { if(c) mvSet.add(def.mov()); else mvSet.remove(def.mov()); });
                Tooltip tt = new Tooltip(def.tip());
                tt.setStyle("-fx-font-size:12px;");
                Tooltip.install(cb, tt);
                movBox.getChildren().add(cb);
            }

            row.getChildren().addAll(lLane, lDir, vehBox, movBox);
            laneRows.getChildren().add(row);
        }

        scroll.setContent(laneRows);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(Math.min(laneCount * 48 + 10, 300));
        // Fix nền trắng ScrollPane — phải set cả viewport background
        scroll.setStyle("-fx-background:#1a2030; -fx-background-color:#1a2030; -fx-border-color:#2a3550; -fx-border-width:1;");
        scroll.getStylesheets().add(
                "data:text/css,.scroll-pane>.viewport{-fx-background-color:#1a2030;}" +
                        ".scroll-pane{-fx-background-color:#1a2030;}" +
                        ".check-box .text{-fx-fill:#c8d0e8;}" +
                        ".scroll-bar:vertical .thumb{-fx-background-color:#3a4560;}" +
                        ".scroll-bar:vertical .track{-fx-background-color:#141824;}"
        );
        root.getChildren().add(scroll);

        // ── Làn khẩn cấp (chỉ cho HighwaySegment) ──────────────
        boolean isHighway = targetSeg instanceof HighwaySegment;
        boolean[] emergencyLaneState = { isHighway && ((HighwaySegment) targetSeg).hasEmergencyLane() };
        if (isHighway) {
            javafx.scene.layout.HBox emergRow = new javafx.scene.layout.HBox(10);
            emergRow.setAlignment(Pos.CENTER_LEFT);
            emergRow.setStyle("-fx-background-color:#1e2a1e; -fx-border-color:#3a5030;" +
                    " -fx-border-width:1; -fx-padding:8 12 8 12; -fx-background-radius:4;");

            CheckBox cbEmLane = new CheckBox("🟠  Làn khẩn cấp (ngoài cùng bên phải)");
            cbEmLane.setSelected(emergencyLaneState[0]);
            cbEmLane.setTextFill(Color.web("#ff9944"));
            cbEmLane.setStyle("-fx-font-size:12px; -fx-font-weight:bold;");

            Label emNote = new Label("Chỉ cho phép xe cứu thương/cứu hỏa/cảnh sát đang làm nhiệm vụ" +
                    " và xe gặp sự cố dừng đỗ khẩn cấp.");
            emNote.setStyle("-fx-text-fill:#8090b0; -fx-font-size:11px;");
            emNote.setWrapText(true);

            cbEmLane.setOnAction(ev -> emergencyLaneState[0] = cbEmLane.isSelected());

            VBox emBox = new VBox(4, cbEmLane, emNote);
            emergRow.getChildren().add(emBox);
            root.getChildren().add(emergRow);
        }

        // Ghi chú
        Label note = new Label("⚙ Cấu hình này áp dụng cho đường vẽ tiếp theo. Nhấn \"Áp dụng\" để lưu.");
        note.setStyle("-fx-text-fill:#5a7090; -fx-font-size:11px;");
        root.getChildren().add(note);

        // Buttons
        Button btnApply = new Button("✔ Áp dụng");
        btnApply.setStyle("-fx-background-color:#2a5a2a; -fx-text-fill:white; -fx-font-weight:bold; " +
                "-fx-cursor:hand; -fx-padding:5 16 5 16; -fx-background-radius:5;");
        Button btnCancel = new Button("Hủy");
        btnCancel.setStyle("-fx-background-color:#3a3030; -fx-text-fill:#c8d0e8; " +
                "-fx-cursor:hand; -fx-padding:5 16 5 16; -fx-background-radius:5;");
        btnApply.setOnAction(e -> {
            perLaneVehicles  = vehPerLane;
            perLaneMovements = movPerLane;
            usePerLaneConfig = true;

            // Nếu đang sửa đường có sẵn → apply ngay lên segment đó
            if (targetSeg != null) {
                List<Lane> newLanes = new ArrayList<>();
                for (int i = 0; i < laneCount; i++) {
                    Lane old = i < targetSeg.getLanes().size() ? targetSeg.getLanes().get(i)
                            : targetSeg.getLanes().get(targetSeg.getLanes().size() - 1);
                    Lane updated = new Lane(i, old.getDirection(), old.getWidth(),
                            vehPerLane[i].isEmpty() ? EnumSet.of(Lane.VehicleCategory.CAR) : vehPerLane[i],
                            movPerLane[i].isEmpty() ? EnumSet.of(Lane.Movement.STRAIGHT)   : movPerLane[i],
                            old.getLeftMarking(), old.getRightMarking());
                    newLanes.add(updated);
                }
                RoadSegment updated;
                if (targetSeg instanceof HighwaySegment hwy) {
                    // Rebuild HighwaySegment với trạng thái làn khẩn cấp mới
                    try {
                        updated = new HighwaySegment(
                                targetSeg.getStartX(), targetSeg.getStartY(),
                                targetSeg.getEndX(),   targetSeg.getEndY(),
                                newLanes, hwy.getMinSpeedLimit(), emergencyLaneState[0]);
                    } catch (IllegalArgumentException ex) {
                        // Không đủ làn cho emergency lane → báo lỗi nhẹ, giữ nguyên
                        new Alert(Alert.AlertType.WARNING,
                                "Cần ít nhất 3 làn để bật làn khẩn cấp.", ButtonType.OK).showAndWait();
                        return;
                    }
                    updated.setConnector(targetSeg.isConnector());
                } else {
                    updated = targetSeg.withNewLanes(newLanes);
                }
                view.replaceSegment(targetSeg, updated);
            }
            dlg.close();
        });
        btnCancel.setOnAction(e -> { usePerLaneConfig = false; dlg.close(); });

        HBox btnRow = new HBox(10, btnApply, btnCancel);
        btnRow.setAlignment(Pos.CENTER_RIGHT);
        root.getChildren().add(btnRow);

        dlg.setScene(new Scene(root, 700, Math.min(laneCount * 46 + 180, 520)));
        dlg.getScene().getStylesheets().add("data:text/css,.check-box .text{-fx-fill:#c8d0e8;}");
        dlg.show();
    }

    @SuppressWarnings("unchecked")
    private Set<Lane.VehicleCategory>[] perLaneVehicles  = null;
    @SuppressWarnings("unchecked")
    private Set<Lane.Movement>[]        perLaneMovements = null;
    private boolean usePerLaneConfig = false;

    private CheckBox miniCb(String label, boolean selected, java.util.function.Consumer<Boolean> onChange) {
        CheckBox cb = new CheckBox(label);
        cb.setSelected(selected);
        cb.setTextFill(Color.web("#c8d0e8"));
        cb.setStyle("-fx-font-size:11px;");
        cb.setOnAction(e -> onChange.accept(cb.isSelected()));
        return cb;
    }

    private void rebuildAllowedVehicles(CheckBox cbCar, CheckBox cbMoto, CheckBox cbBus,
                                        CheckBox cbTruck, CheckBox cbBike) {
        allowedVehicles = EnumSet.noneOf(Lane.VehicleCategory.class);
        if (cbCar.isSelected())   allowedVehicles.add(Lane.VehicleCategory.CAR);
        if (cbMoto.isSelected())  allowedVehicles.add(Lane.VehicleCategory.MOTORBIKE);
        if (cbBus.isSelected())   allowedVehicles.add(Lane.VehicleCategory.BUS);
        if (cbTruck.isSelected()) allowedVehicles.add(Lane.VehicleCategory.TRUCK);
        if (cbBike.isSelected())  allowedVehicles.add(Lane.VehicleCategory.BICYCLE);
        if (allowedVehicles.isEmpty()) allowedVehicles.add(Lane.VehicleCategory.CAR);
    }

    private HBox buildCtxEdit() {
        Label tip = new Label(
                "✏️  Hover chuột lên ranh giới giữa 2 làn → vạch sáng  •  " +
                        "Chuột PHẢI → chọn loại: Nét đứt / Nét liền / Nét đôi / Không vạch");
        tip.setStyle("-fx-text-fill:#c8d0e8; -fx-font-size:11px;");
        return hbox(tip);
    }

    private HBox buildCtxDelete() {
        Label tip = new Label(
                "🗑  Hover lên đoạn đường hoặc nút giao → đỏ lên  •  Click TRÁI để xóa  •  Ctrl+Z = undo");
        tip.setStyle("-fx-text-fill:#ff7070; -fx-font-size:11px;");
        return hbox(tip);
    }

    private HBox buildCtxIntersection(SimulationView view) {
        // ── Loại giao lộ ──
        Label lblType = new Label("Loại:");
        lblType.setStyle("-fx-text-fill:#c8d0e8; -fx-font-size:12px;");
        ComboBox<String[]> typeBox = new ComboBox<>();
        typeBox.getItems().addAll(
                new String[]{"🔺 Ngã ba",         "3"},
                new String[]{"✚ Ngã tư",          "4"},
                new String[]{"⭐ Ngã năm",         "5"},
                new String[]{"🔄 Vòng xuyến nhỏ", "ROUNDABOUT_S"},
                new String[]{"🔄 Vòng xuyến lớn", "ROUNDABOUT_L"}
        );
        typeBox.setValue(new String[]{"✚ Ngã tư", "4"});
        typeBox.setPrefWidth(168);
        typeBox.setStyle("-fx-background-color:#2a3550;");
        typeBox.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(String[] o) { return o == null ? "" : o[0]; }
            @Override public String[] fromString(String s) { return null; }
        });
        typeBox.setButtonCell(new javafx.scene.control.ListCell<>() {
            @Override protected void updateItem(String[] item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item[0]);
                setStyle("-fx-text-fill:white; -fx-font-weight:bold; -fx-font-size:12px; -fx-background-color:transparent;");
            }
        });
        typeBox.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
            @Override protected void updateItem(String[] item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(""); return; }
                setText(item[0]);
                setStyle("-fx-text-fill:#c8d0e8; -fx-font-size:12px; -fx-background-color:transparent;");
            }
        });
        view.setIntersectionTypeToPlace("4");

        // ── Bán kính vòng xuyến: Slider + TextField nhập tay ──
        Label lblR = new Label("Bán kính:");
        lblR.setStyle("-fx-text-fill:#c8d0e8; -fx-font-size:12px;");
        Slider sliderR = new Slider(30, 250, 60);
        sliderR.setPrefWidth(90);
        sliderR.setMajorTickUnit(30);
        sliderR.setSnapToTicks(false);
        TextField tfR = new TextField("60");
        tfR.setPrefWidth(48);
        tfR.setStyle("-fx-background-color:#2a3550; -fx-text-fill:#e8d44d; -fx-font-size:12px; -fx-border-color:#3a4560;");
        Label lblRUnit = new Label("u");
        lblRUnit.setStyle("-fx-text-fill:#c8d0e8; -fx-font-size:11px;");

        // Slider → TextField
        sliderR.valueProperty().addListener((ob, ov, nv) -> {
            roundaboutRadius = Math.round(nv.doubleValue());
            tfR.setText(String.valueOf((int)roundaboutRadius));
            view.setRoundaboutRadius(roundaboutRadius);
        });
        // TextField → Slider
        tfR.setOnAction(e -> {
            try {
                double v = Math.max(30, Math.min(250, Double.parseDouble(tfR.getText().trim())));
                roundaboutRadius = v;
                sliderR.setValue(v);
                tfR.setText(String.valueOf((int)v));
                view.setRoundaboutRadius(v);
            } catch (NumberFormatException ex) { tfR.setText(String.valueOf((int)roundaboutRadius)); }
        });
        tfR.focusedProperty().addListener((ob, ov, focused) -> { if (!focused) tfR.getOnAction().handle(null); });

        // Hiện/ẩn slider bán kính tùy loại — handled in tip block below
        for (javafx.scene.Node n : new javafx.scene.Node[]{lblR, sliderR, tfR, lblRUnit})
            n.setVisible(false);

        Label tip = new Label("💡 Click để đặt tâm  •  Ctrl+Z = undo");
        tip.setStyle("-fx-text-fill:#4a6080; -fx-font-size:11px;");

        // ── Số nhánh vòng xuyến ──
        Label lblBranch = new Label("Nhánh:");
        lblBranch.setStyle("-fx-text-fill:#c8d0e8; -fx-font-size:12px;");
        Spinner<Integer> spinBranch = new Spinner<>(3, 8, 4);
        spinBranch.setPrefWidth(64);
        spinBranch.setStyle("-fx-background-color:#2a3550; -fx-background-radius:4; -fx-border-color:#3a4560;");
        spinBranch.getEditor().setStyle("-fx-background-color:#2a3550; -fx-text-fill:#c8d0e8; -fx-font-size:12px; -fx-alignment:center;");
        spinBranch.valueProperty().addListener((ob, ov, nv) -> { if (nv != null) view.setRoundaboutBranches(nv); });
        lblBranch.setVisible(false); spinBranch.setVisible(false);

        typeBox.setOnAction(e -> {
            String[] sel = typeBox.getValue();
            if (sel == null) return;
            view.setIntersectionTypeToPlace(sel[1]);
            boolean isRound = sel[1].startsWith("ROUNDABOUT");
            for (javafx.scene.Node n : new javafx.scene.Node[]{lblR, sliderR, tfR, lblRUnit, lblBranch, spinBranch})
                n.setVisible(isRound);
        });

        HBox box = hbox(lblType, typeBox, vsep(), lblBranch, spinBranch, vsep(), lblR, sliderR, tfR, lblRUnit, vsep(), tip);
        box.setSpacing(8);
        return box;
    }

    // ════════════════════════════════════════════════════════
    // WIDGET HELPERS
    // ════════════════════════════════════════════════════════

    private ToggleButton modeBtn(String icon, String label, String tooltip) {
        ToggleButton tb = new ToggleButton(icon + "  " + label);
        tb.setTooltip(new Tooltip(tooltip));
        String base = "-fx-font-size:13px; -fx-font-weight:bold; -fx-cursor:hand; " +
                "-fx-pref-height:32px; -fx-padding:3 14 3 14; -fx-background-radius:6;";
        tb.setStyle(base + "-fx-background-color:#2e3d52; -fx-text-fill:#c8d0e8;");
        tb.selectedProperty().addListener((o, was, is) ->
                tb.setStyle(base + (is
                        ? "-fx-background-color:#e8d44d; -fx-text-fill:#1a1f2e;"
                        : "-fx-background-color:#2e3d52; -fx-text-fill:#c8d0e8;")));
        return tb;
    }

    private ToggleButton smallToggle(String label, boolean selected) {
        ToggleButton tb = new ToggleButton(label);
        String base = "-fx-font-size:11px; -fx-cursor:hand; -fx-pref-height:24px; " +
                "-fx-padding:2 8 2 8; -fx-background-radius:4;";
        tb.setStyle(base + "-fx-background-color:#2a3550; -fx-text-fill:#c8d0e8;");
        tb.selectedProperty().addListener((o, was, is) ->
                tb.setStyle(base + (is
                        ? "-fx-background-color:#3a6090; -fx-text-fill:white;"
                        : "-fx-background-color:#2a3550; -fx-text-fill:#c8d0e8;")));
        tb.setSelected(selected);
        return tb;
    }

    private Label ctxLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill:#c8d0e8; -fx-font-size:12px;");
        return l;
    }

    private CheckBox movCb(String text, boolean selected) {
        CheckBox cb = new CheckBox(text);
        cb.setSelected(selected);
        cb.setTextFill(Color.web("#c8d0e8"));
        cb.setStyle("-fx-font-size:11px;");
        return cb;
    }

    private CheckBox vehCb(String text, Lane.VehicleCategory cat, boolean selected) {
        CheckBox cb = new CheckBox(text);
        cb.setSelected(selected);
        cb.setTextFill(Color.web("#c8d0e8"));
        cb.setStyle("-fx-font-size:11px;");
        return cb;
    }

    private Button actionBtn(String label, String tooltip) {
        Button b = new Button(label);
        b.setTooltip(new Tooltip(tooltip));
        b.setStyle("-fx-background-color:#243040; -fx-text-fill:#c8d0e8; " +
                "-fx-font-weight:bold; -fx-cursor:hand; -fx-pref-height:28px; " +
                "-fx-padding:2 10 2 10; -fx-background-radius:5;");
        return b;
    }

    private CheckBox styledCb(String text, boolean selected,
                              javafx.event.EventHandler<javafx.event.ActionEvent> handler) {
        CheckBox cb = new CheckBox(text);
        cb.setSelected(selected);
        cb.setTextFill(Color.web("#c8d0e8"));
        cb.setOnAction(handler);
        return cb;
    }

    private void styleCombo(ComboBox<String> box) {
        box.setButtonCell(new javafx.scene.control.ListCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item);
                setStyle("-fx-text-fill:white; -fx-font-weight:bold; -fx-font-size:12px; -fx-background-color:transparent;");
            }
        });
        box.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(""); return; }
                setText(item);
                setStyle("-fx-text-fill:#c8d0e8; -fx-font-size:12px; -fx-background-color:transparent;");
            }
        });
    }

    private Separator vsep() {
        Separator s = new Separator(Orientation.VERTICAL);
        s.setPadding(new Insets(0, 2, 0, 2));
        return s;
    }

    private HBox hbox(javafx.scene.Node... nodes) {
        HBox b = new HBox(6, nodes);
        b.setAlignment(Pos.CENTER_LEFT);
        return b;
    }

    public static void main(String[] args) { launch(args); }
}