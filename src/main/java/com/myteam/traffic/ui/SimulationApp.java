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
 * SimulationApp â€” Entry point vá»›i UI 2 táº§ng má»Ÿ rá»™ng Ä‘Æ°á»£c.
 * PhiÃªn báº£n má»Ÿ rá»™ng: thÃªm tuá»³ chá»‰nh chiá»u rá»™ng lÃ n, chiá»u Ä‘Æ°á»ng,
 * loáº¡i xe cho phÃ©p, bÃ¡n kÃ­nh vÃ²ng xuyáº¿n.
 */
public class SimulationApp extends Application {

    // â”€â”€ Cáº¥u hÃ¬nh lÃ n máº·c Ä‘á»‹nh â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private SimulationView view; // field Ä‘á»ƒ dialog methods dÃ¹ng Ä‘Æ°á»£c
    private double currentLaneWidth  = 3.5;
    private boolean onewayMode       = false;
    private Set<Lane.VehicleCategory> allowedVehicles =
            EnumSet.of(Lane.VehicleCategory.CAR, Lane.VehicleCategory.MOTORBIKE,
                    Lane.VehicleCategory.BUS);
    private Set<Lane.Movement> currentMovements =
            EnumSet.of(Lane.Movement.STRAIGHT, Lane.Movement.LEFT, Lane.Movement.RIGHT);
    private double roundaboutRadius  = 60.0;

    // â”€â”€ Lane factories â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private Lane fwdLane(int idx) {
        return new Lane(idx, Lane.Direction.FORWARD, currentLaneWidth,
                allowedVehicles, currentMovements,
                Lane.MarkingType.DASHED, Lane.MarkingType.DASHED);
    }

    private Lane bwdLane(int idx) {
        // LÃ n ngÆ°á»£c chiá»u: hÆ°á»›ng ráº½ lÃ  Ä‘á»‘i xá»©ng (STRAIGHT + Ä‘áº£o LEFT/RIGHT)
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
     * Static fallback â€” dÃ¹ng khi laneFactory chÆ°a Ä‘Æ°á»£c set (chiá»u rá»™ng 3.5m máº·c Ä‘á»‹nh).
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
        // Váº¡ch tim Ä‘Æ°á»ng â†’ YELLOW_DASHED
        if (fwd > 0 && bwd > 0) {
            l.set(fwd - 1, l.get(fwd - 1).withRightMarking(Lane.MarkingType.YELLOW_DASHED));
            l.set(fwd,     l.get(fwd)    .withLeftMarking (Lane.MarkingType.YELLOW_DASHED));
        }
        return l;
    }

    /**
     * Instance version â€” dÃ¹ng cáº¥u hÃ¬nh hiá»‡n táº¡i (chiá»u rá»™ng, loáº¡i xe, chiá»u Ä‘Æ°á»ng).
     * ÄÆ°á»£c truyá»n vÃ o SimulationView qua setLaneFactory(this::createLanesInstance).
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
        // Váº¡ch tim Ä‘Æ°á»ng (giá»¯a lÃ n FORWARD cuá»‘i vÃ  BACKWARD Ä‘áº§u) â†’ YELLOW_DASHED
        if (fwd > 0 && bwd > 0) {
            Lane last  = lanes.get(fwd - 1);
            Lane first = lanes.get(fwd);
            lanes.set(fwd - 1, last .withRightMarking(Lane.MarkingType.YELLOW_DASHED));
            lanes.set(fwd,     first.withLeftMarking (Lane.MarkingType.YELLOW_DASHED));
        }
        return lanes;
    }

    // â”€â”€ Start â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    @Override
    public void start(Stage stage) {
        SimulationView view = new SimulationView(1200, 750);
        this.view = view;
        RoadNetwork network = new RoadNetwork();
        view.setNetwork(network);
        view.setLaneFactory(this::createLanesInstance);

        com.myteam.traffic.controller.TrafficController trafficController =
                new com.myteam.traffic.controller.TrafficController(network);
        trafficController.addLight(new com.myteam.traffic.light.NoCountdownLight(30,30,30));
        com.myteam.traffic.controller.VehicleSpawner spawner =
                new com.myteam.traffic.controller.VehicleSpawner(trafficController, network);
        view.setController(trafficController);
        view.setSpawner(spawner);

        // â•â•â•â• TOP BAR â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
        ToggleButton btnModePan    = modeBtn("ðŸ–", "Di chuyá»ƒn",  "KÃ©o vÃ  xoay báº£n Ä‘á»“");
        ToggleButton btnModeDraw   = modeBtn("ðŸ›£", "Váº½ Ä‘Æ°á»ng",   "Váº½ Ä‘oáº¡n Ä‘Æ°á»ng má»›i");
        ToggleButton btnModeEdit   = modeBtn("âœï¸", "Sá»­a váº¡ch",   "Hover váº¡ch káº» â†’ menu chá»n loáº¡i");
        ToggleButton btnModeInter  = modeBtn("ðŸ”€", "Giao lá»™",    "Click Ä‘á»ƒ Ä‘áº·t nÃºt giao thÃ´ng");
        ToggleButton btnModeDelete = modeBtn("ðŸ—‘", "XÃ³a",        "Hover vÃ o Ä‘Æ°á»ng/nÃºt giao â†’ click Ä‘á»ƒ xÃ³a");
        ToggleButton btnModeSim    = modeBtn("🚗", "Mo phong", "Chay mo phong & spawn xe theo ty le");

        // NÃºt xÃ³a mÃ u Ä‘á» khi active
        btnModeDelete.selectedProperty().addListener((o, was, is) -> {
            String base = "-fx-font-size:13px; -fx-font-weight:bold; -fx-cursor:hand; " +
                    "-fx-pref-height:32px; -fx-padding:3 14 3 14; -fx-background-radius:6;";
            btnModeDelete.setStyle(base + (is
                    ? "-fx-background-color:#c0392b; -fx-text-fill:white;"
                    : "-fx-background-color:#2e3d52; -fx-text-fill:#c8d0e8;"));
        });

        ToggleGroup modeGroup = new ToggleGroup();
        for (ToggleButton b : new ToggleButton[]{btnModePan, btnModeDraw, btnModeEdit, btnModeInter, btnModeDelete, btnModeSim})
            b.setToggleGroup(modeGroup);
        btnModePan.setSelected(true);

        HBox modeBox = new HBox(4, btnModePan, btnModeDraw, btnModeEdit, btnModeInter, btnModeDelete, btnModeSim);
        modeBox.setAlignment(Pos.CENTER_LEFT);

        Button btnZoomIn  = actionBtn("ðŸ”+", "PhÃ³ng to  (Scroll)");
        Button btnZoomOut = actionBtn("ðŸ”Ž-", "Thu nhá»  (Scroll)");
        Button btnUndo    = actionBtn("â†© HoÃ n tÃ¡c", "Ctrl + Z");
        Button btnClear   = actionBtn("ðŸ—‘ XÃ³a sáº¡ch", "XÃ³a toÃ n bá»™");

        btnZoomIn .setOnAction(e -> view.zoomIn());
        btnZoomOut.setOnAction(e -> view.zoomOut());
        btnUndo   .setOnAction(e -> view.undo());
        btnClear  .setOnAction(e -> { view.saveSnapshot(); view.setNetwork(new RoadNetwork()); });

        CheckBox cbGrid   = styledCb("LÆ°á»›i",       true,  e -> view.setShowGrid  (((CheckBox)e.getSource()).isSelected()));
        CheckBox cbLabels = styledCb("NhÃ£n Ä‘Æ°á»ng", true,  e -> view.setShowLabels(((CheckBox)e.getSource()).isSelected()));

        Label hint = new Label("ðŸ–± TRÃI: Thao tÃ¡c   PHáº¢I: KÃ©o báº£n Ä‘á»“   SCROLL: Zoom");
        hint.setStyle("-fx-text-fill:#e8d44d; -fx-font-size:11px; -fx-padding:0 0 0 8;");

        HBox topBar = new HBox(10,
                modeBox,
                vsep(), new HBox(4, btnZoomIn, btnZoomOut, btnUndo, btnClear),
                vsep(), new HBox(10, cbGrid, cbLabels),
                vsep(), hint);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(7, 14, 7, 14));
        topBar.setStyle("-fx-background-color:#141824;");

        // â•â•â•â• CONTEXT BAR â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
        HBox ctxPan    = buildCtxPan();
        HBox ctxDraw   = buildCtxDraw(view);
        HBox ctxEdit   = buildCtxEdit();
        HBox ctxInter  = buildCtxIntersection(view);
        HBox ctxDelete = buildCtxDelete();
        HBox ctxSim    = buildCtxSimulation(view, spawner);

        StackPane ctxStack = new StackPane(ctxPan, ctxDraw, ctxEdit, ctxInter, ctxDelete, ctxSim);
        ctxPan.setVisible(true);
        for (HBox h : new HBox[]{ctxDraw, ctxEdit, ctxInter, ctxDelete, ctxSim}) {
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
            ctxEdit  .setVisible(nw == btnModeEdit);
            ctxInter .setVisible(nw == btnModeInter);
            ctxDelete.setVisible(nw == btnModeDelete);
            ctxSim   .setVisible(nw == btnModeSim);
            if (nw == btnModePan)    view.setInteractionType(SimulationView.InteractionType.PAN);
            if (nw == btnModeDraw)   view.setInteractionType(SimulationView.InteractionType.DRAW_ROAD);
            if (nw == btnModeEdit)   view.setInteractionType(SimulationView.InteractionType.EDIT_MARKINGS);
            if (nw == btnModeInter)  view.setInteractionType(SimulationView.InteractionType.PLACE_INTERSECTION);
            if (nw == btnModeDelete) view.setInteractionType(SimulationView.InteractionType.DELETE);
            if (nw == btnModeSim)    view.setInteractionType(SimulationView.InteractionType.PAN);
        });

        // â•â•â•â• LAYOUT â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
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

        stage.setTitle("Traffic Builder â€” TrÃ¬nh Dá»±ng LÆ°á»›i Giao ThÃ´ng");
        stage.setScene(scene);
        stage.show();
        view.resetView();
    }

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // CONTEXT BARS
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    private HBox buildCtxPan() {
        Label lbl = new Label("ðŸ–  Giá»¯ chuá»™t PHáº¢I Ä‘á»ƒ kÃ©o báº£n Ä‘á»“  â€¢  Scroll Ä‘á»ƒ zoom.");
        lbl.setStyle("-fx-text-fill:#5a7090; -fx-font-size:11px;");
        return hbox(lbl);
    }

    private HBox buildCtxDraw(SimulationView view) {
        // â”€â”€ Sá»‘ lÃ n â€” Spinner â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        Label lblLane = ctxLabel("Sá»‘ lÃ n:");
        Spinner<Integer> spinLane = new Spinner<>(1, 16, 4);
        spinLane.setEditable(true);
        spinLane.setPrefWidth(72);
        spinLane.setStyle("-fx-background-color:#2a3550; -fx-background-radius:4; -fx-border-color:#3a4560; -fx-border-radius:4;");
        spinLane.getEditor().setStyle("-fx-background-color:#2a3550; -fx-text-fill:#c8d0e8; -fx-font-weight:bold; -fx-font-size:12px; -fx-alignment:center;");
        spinLane.getEditor().setAlignment(javafx.geometry.Pos.CENTER);
        view.setCurrentLaneConfig(4);
        // Commit khi Enter hoáº·c focus out
        spinLane.getEditor().setOnAction(e -> {
            try {
                int v = Math.max(1, Math.min(16, Integer.parseInt(spinLane.getEditor().getText().trim())));
                spinLane.getValueFactory().setValue(v);
                view.setCurrentLaneConfig(v);
            } catch (NumberFormatException ex) { spinLane.getEditor().setText(spinLane.getValue().toString()); }
            // Blur focus â†’ con trá» biáº¿n máº¥t, ngÆ°á»i dÃ¹ng tháº¥y Ä‘Ã£ set xong
            spinLane.getParent().requestFocus();
        });
        spinLane.getEditor().focusedProperty().addListener((ob, ov, focused) -> {
            if (!focused) spinLane.getEditor().getOnAction().handle(null);
        });
        spinLane.valueProperty().addListener((ob, ov, nv) -> { if (nv != null) view.setCurrentLaneConfig(nv); });

        // â”€â”€ Chiá»u rá»™ng lÃ n: Slider + TextField â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        Label lblW = ctxLabel("Rá»™ng lÃ n:");
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

        // â”€â”€ Chiá»u Ä‘Æ°á»ng â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        Label lblDir = ctxLabel("Chiá»u:");
        ToggleButton btnTwo = smallToggle("â‡„ Hai chiá»u", true);
        ToggleButton btnOne = smallToggle("â†’ Má»™t chiá»u", false);
        ToggleGroup dirGroup = new ToggleGroup();
        btnTwo.setToggleGroup(dirGroup); btnOne.setToggleGroup(dirGroup);
        btnTwo.setSelected(true);
        dirGroup.selectedToggleProperty().addListener((ob, ov, nv) -> onewayMode = (nv == btnOne));

        // Callback: khi nháº¥n nÃºt "âœ Sá»­a" trong popup info Ä‘Æ°á»ng
        view.setOnSegmentSelected(seg -> {
            if (seg == null) return;
            showLaneConfigDialog(seg.getLaneCount(), seg);
            view.clearSelectedSegment();
        });

        // â”€â”€ Cao tá»‘c â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        Label lblHwy = ctxLabel("Cao tá»‘c:");
        CheckBox cbHwy = new CheckBox("Báº­t");
        cbHwy.setTextFill(Color.web("#6bc5ff")); cbHwy.setStyle("-fx-font-size:11px;");
        Label lblSpeed = ctxLabel("Tá»‘c Ä‘á»™ min:");
        TextField tfSpeed = new TextField("60");
        tfSpeed.setPrefWidth(44);
        tfSpeed.setStyle("-fx-background-color:#2a3550; -fx-text-fill:#e8d44d; -fx-font-size:12px; -fx-border-color:#3a4560;");
        Label lblSpeedU = ctxLabel("km/h");
        CheckBox cbEmerg = new CheckBox("LÃ n KCáº¥p");
        cbEmerg.setTextFill(Color.web("#ff9944")); cbEmerg.setStyle("-fx-font-size:11px;");
        for (javafx.scene.Node n : new javafx.scene.Node[]{lblSpeed, tfSpeed, lblSpeedU, cbEmerg}) n.setVisible(false);
        cbHwy.setOnAction(e -> {
            boolean on = cbHwy.isSelected();
            view.setHighwayMode(on);
            for (javafx.scene.Node n : new javafx.scene.Node[]{lblSpeed, tfSpeed, lblSpeedU, cbEmerg}) n.setVisible(on);
        });
        tfSpeed.setOnAction(e -> {
            try { view.setHighwayMinSpeed(Double.parseDouble(tfSpeed.getText().trim())); }
            catch (NumberFormatException ex) {}
            tfSpeed.getParent().requestFocus(); // blur â†’ con trá» biáº¿n máº¥t
        });
        tfSpeed.focusedProperty().addListener((ob, ov, focused) -> {
            if (!focused) { try { view.setHighwayMinSpeed(Double.parseDouble(tfSpeed.getText().trim())); } catch (NumberFormatException ignored) {} }
        });
        cbEmerg.setOnAction(e -> view.setEmergencyLane(cbEmerg.isSelected()));

        // â”€â”€ Auto-intersect â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        CheckBox cbAuto = styledCb("Tá»± táº¡o nÃºt giao", true,
                e2 -> view.setAutoIntersect(((CheckBox)e2.getSource()).isSelected()));

        HBox box = hbox(
                lblLane, spinLane,
                vsep(), lblW, sliderW, tfW, lblWUnit,
                vsep(), lblDir, btnTwo, btnOne,
                vsep(), lblHwy, cbHwy, lblSpeed, tfSpeed, lblSpeedU, cbEmerg,
                vsep(), cbAuto);
        box.setSpacing(6);
        return box;
    }

    /**
     * Popup cáº¥u hÃ¬nh chi tiáº¿t tá»«ng lÃ n: loáº¡i xe + hÆ°á»›ng ráº½ riÃªng cho lÃ n xuÃ´i/ngÆ°á»£c.
     */
    private void showLaneConfigDialog(int laneCount, RoadSegment targetSeg) {
        Stage dlg = new Stage();
        dlg.setTitle(targetSeg != null
                ? "Sá»­a chi tiáº¿t lÃ n â€” Ä‘Æ°á»ng Ä‘ang hover"
                : "Cáº¥u hÃ¬nh chi tiáº¿t lÃ n â€” Ã¡p dá»¥ng cho Ä‘Æ°á»ng váº½ tiáº¿p");
        dlg.initModality(javafx.stage.Modality.APPLICATION_MODAL);

        String DARK = "-fx-background-color:#1a2030;";
        String CELL = "-fx-background-color:#212d3a; -fx-border-color:#2a3550; -fx-border-width:0 0 1 0; -fx-padding:6 10 6 10;";

        VBox root = new VBox(10);
        root.setStyle(DARK + " -fx-padding:16;");

        Label title = new Label("Cáº¥u hÃ¬nh " + laneCount + " lÃ n â€” Ã¡p dá»¥ng cho Ä‘Æ°á»ng váº½ tiáº¿p theo");
        title.setStyle("-fx-text-fill:#e8d44d; -fx-font-size:13px; -fx-font-weight:bold;");
        root.getChildren().add(title);

        // Header row
        HBox header = new HBox(0);
        for (String h : new String[]{"LÃ n", "Chiá»u", "Loáº¡i xe cho phÃ©p", "HÆ°á»›ng Ä‘Æ°á»£c phÃ©p Ä‘i"}) {
            Label lh = new Label(h);
            lh.setStyle("-fx-text-fill:#8090b0; -fx-font-size:11px; -fx-font-weight:bold; -fx-padding:4 10 4 10;");
            lh.setPrefWidth(h.equals("LÃ n") || h.equals("Chiá»u") ? 70 : 260);
            header.getChildren().add(lh);
        }
        header.setStyle("-fx-background-color:#141824; -fx-border-color:#2a3550; -fx-border-width:0 0 1 0;");
        root.getChildren().add(header);

        // Per-lane rows
        int fwd = onewayMode ? laneCount : (laneCount + 1) / 2;
        int bwd = onewayMode ? 0 : laneCount / 2;

        // Cáº¥u hÃ¬nh riÃªng per-lane (dÃ¹ng lambda-captured arrays)
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
            String dirLabel = isFwd ? "â†’ XuÃ´i" : "â† NgÆ°á»£c";
            Color dirColor  = isFwd ? Color.web("#6bc5ff") : Color.web("#ff9966");

            HBox row = new HBox(0);
            row.setStyle(CELL);
            row.setAlignment(Pos.CENTER_LEFT);

            // LÃ n sá»‘
            Label lLane = new Label("LÃ n " + (i + 1));
            lLane.setStyle("-fx-text-fill:#c8d0e8; -fx-font-size:12px; -fx-font-weight:bold; -fx-min-width:70px;");

            // Chiá»u
            Label lDir = new Label(dirLabel);
            lDir.setTextFill(dirColor);
            lDir.setStyle("-fx-font-size:11px; -fx-min-width:70px;");

            // Loáº¡i xe (checkboxes nhá» + tooltip)
            HBox vehBox = new HBox(6);
            vehBox.setPrefWidth(260); vehBox.setAlignment(Pos.CENTER_LEFT);
            record VehDef(String icon, String tip, Lane.VehicleCategory cat) {}
            var vehDefs = new VehDef[]{
                    new VehDef("ðŸš—", "Ã” tÃ´",          Lane.VehicleCategory.CAR),
                    new VehDef("ðŸ›µ", "Xe mÃ¡y",         Lane.VehicleCategory.MOTORBIKE),
                    new VehDef("ðŸšŒ", "Xe buÃ½t",        Lane.VehicleCategory.BUS),
                    new VehDef("ðŸš›", "Xe táº£i náº·ng",    Lane.VehicleCategory.TRUCK),
                    new VehDef("ðŸš²", "Xe Ä‘áº¡p",         Lane.VehicleCategory.BICYCLE),
                    new VehDef("ðŸš¨", "Xe kháº©n cáº¥p",   Lane.VehicleCategory.EMERGENCY),
            };
            for (var def : vehDefs) {
                CheckBox cb = miniCb(def.icon(), vehPerLane[idx].contains(def.cat()),
                        c -> { if(c) vehPerLane[idx].add(def.cat()); else vehPerLane[idx].remove(def.cat()); });
                Tooltip tt = new Tooltip(def.tip());
                tt.setStyle("-fx-font-size:12px;");
                Tooltip.install(cb, tt);
                vehBox.getChildren().add(cb);
            }

            // HÆ°á»›ng Ä‘Æ°á»£c phÃ©p Ä‘i (checkboxes nhá» + tooltip)
            HBox movBox = new HBox(6);
            movBox.setPrefWidth(250); movBox.setAlignment(Pos.CENTER_LEFT);
            Set<Lane.Movement> mvSet = movPerLane[idx];
            record MovDef(String icon, String tip, Lane.Movement mov) {}
            var movDefs = isFwd
                    ? new MovDef[]{
                    new MovDef("â†‘ Tháº³ng",   "Äi tháº³ng",       Lane.Movement.STRAIGHT),
                    new MovDef("â†° TrÃ¡i",    "Ráº½ trÃ¡i",        Lane.Movement.LEFT),
                    new MovDef("â†± Pháº£i",    "Ráº½ pháº£i",        Lane.Movement.RIGHT),
                    new MovDef("â†© Quay Ä‘áº§u","Quay Ä‘áº§u xe",    Lane.Movement.U_TURN),
            }
                    : new MovDef[]{
                    new MovDef("â†‘ Tháº³ng",   "Äi tháº³ng",       Lane.Movement.STRAIGHT),
                    new MovDef("â†± Pháº£i",    "Ráº½ pháº£i (theo chiá»u ngÆ°á»£c)", Lane.Movement.LEFT),
                    new MovDef("â†° TrÃ¡i",    "Ráº½ trÃ¡i (theo chiá»u ngÆ°á»£c)", Lane.Movement.RIGHT),
                    new MovDef("â†© Quay Ä‘áº§u","Quay Ä‘áº§u xe",    Lane.Movement.U_TURN),
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
        // Fix ná»n tráº¯ng ScrollPane â€” pháº£i set cáº£ viewport background
        scroll.setStyle("-fx-background:#1a2030; -fx-background-color:#1a2030; -fx-border-color:#2a3550; -fx-border-width:1;");
        scroll.getStylesheets().add(
                "data:text/css,.scroll-pane>.viewport{-fx-background-color:#1a2030;}" +
                        ".scroll-pane{-fx-background-color:#1a2030;}" +
                        ".check-box .text{-fx-fill:#c8d0e8;}" +
                        ".scroll-bar:vertical .thumb{-fx-background-color:#3a4560;}" +
                        ".scroll-bar:vertical .track{-fx-background-color:#141824;}"
        );
        root.getChildren().add(scroll);

        // Ghi chÃº
        Label note = new Label("âš™ Cáº¥u hÃ¬nh nÃ y Ã¡p dá»¥ng cho Ä‘Æ°á»ng váº½ tiáº¿p theo. Nháº¥n \"Ãp dá»¥ng\" Ä‘á»ƒ lÆ°u.");
        note.setStyle("-fx-text-fill:#5a7090; -fx-font-size:11px;");
        root.getChildren().add(note);

        // Buttons
        Button btnApply = new Button("âœ” Ãp dá»¥ng");
        btnApply.setStyle("-fx-background-color:#2a5a2a; -fx-text-fill:white; -fx-font-weight:bold; " +
                "-fx-cursor:hand; -fx-padding:5 16 5 16; -fx-background-radius:5;");
        Button btnCancel = new Button("Há»§y");
        btnCancel.setStyle("-fx-background-color:#3a3030; -fx-text-fill:#c8d0e8; " +
                "-fx-cursor:hand; -fx-padding:5 16 5 16; -fx-background-radius:5;");
        btnApply.setOnAction(e -> {
            perLaneVehicles  = vehPerLane;
            perLaneMovements = movPerLane;
            usePerLaneConfig = true;

            // Náº¿u Ä‘ang sá»­a Ä‘Æ°á»ng cÃ³ sáºµn â†’ apply ngay lÃªn segment Ä‘Ã³
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
                RoadSegment updated = targetSeg.withNewLanes(newLanes);
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
                "âœï¸  Hover chuá»™t lÃªn ranh giá»›i giá»¯a 2 lÃ n â†’ váº¡ch sÃ¡ng  â€¢  " +
                        "Chuá»™t PHáº¢I â†’ chá»n loáº¡i: NÃ©t Ä‘á»©t / NÃ©t liá»n / NÃ©t Ä‘Ã´i / KhÃ´ng váº¡ch");
        tip.setStyle("-fx-text-fill:#c8d0e8; -fx-font-size:11px;");
        return hbox(tip);
    }

    private HBox buildCtxDelete() {
        Label tip = new Label(
                "ðŸ—‘  Hover lÃªn Ä‘oáº¡n Ä‘Æ°á»ng hoáº·c nÃºt giao â†’ Ä‘á» lÃªn  â€¢  Click TRÃI Ä‘á»ƒ xÃ³a  â€¢  Ctrl+Z = undo");
        tip.setStyle("-fx-text-fill:#ff7070; -fx-font-size:11px;");
        return hbox(tip);
    }

    private HBox buildCtxIntersection(SimulationView view) {
        // â”€â”€ Loáº¡i giao lá»™ â”€â”€
        Label lblType = new Label("Loáº¡i:");
        lblType.setStyle("-fx-text-fill:#c8d0e8; -fx-font-size:12px;");
        ComboBox<String[]> typeBox = new ComboBox<>();
        typeBox.getItems().addAll(
                new String[]{"ðŸ”º NgÃ£ ba",         "3"},
                new String[]{"âœš NgÃ£ tÆ°",          "4"},
                new String[]{"â­ NgÃ£ nÄƒm",         "5"},
                new String[]{"ðŸ”„ VÃ²ng xuyáº¿n nhá»", "ROUNDABOUT_S"},
                new String[]{"ðŸ”„ VÃ²ng xuyáº¿n lá»›n", "ROUNDABOUT_L"}
        );
        typeBox.setValue(new String[]{"âœš NgÃ£ tÆ°", "4"});
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

        // â”€â”€ BÃ¡n kÃ­nh vÃ²ng xuyáº¿n: Slider + TextField nháº­p tay â”€â”€
        Label lblR = new Label("BÃ¡n kÃ­nh:");
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

        // Slider â†’ TextField
        sliderR.valueProperty().addListener((ob, ov, nv) -> {
            roundaboutRadius = Math.round(nv.doubleValue());
            tfR.setText(String.valueOf((int)roundaboutRadius));
            view.setRoundaboutRadius(roundaboutRadius);
        });
        // TextField â†’ Slider
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

        // Hiá»‡n/áº©n slider bÃ¡n kÃ­nh tÃ¹y loáº¡i â€” handled in tip block below
        for (javafx.scene.Node n : new javafx.scene.Node[]{lblR, sliderR, tfR, lblRUnit})
            n.setVisible(false);

        Label tip = new Label("ðŸ’¡ Click Ä‘á»ƒ Ä‘áº·t tÃ¢m  â€¢  Ctrl+Z = undo");
        tip.setStyle("-fx-text-fill:#4a6080; -fx-font-size:11px;");

        // â”€â”€ Sá»‘ nhÃ¡nh vÃ²ng xuyáº¿n â”€â”€
        Label lblBranch = new Label("NhÃ¡nh:");
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

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // WIDGET HELPERS
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

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


    // ============================================================
    // CONTEXT BAR: MO PHONG (spawn xe theo ty le)
    // ============================================================
    private HBox buildCtxSimulation(SimulationView view, com.myteam.traffic.controller.VehicleSpawner spawner) {

        ToggleButton btnPlay = smallToggle("▶ Chay mo phong", false);
        btnPlay.setOnAction(e -> {
            boolean run = btnPlay.isSelected();
            spawner.setEnabled(run);
            view.setSimulationRunning(run);
            btnPlay.setText(run ? "⏸ Tam dung" : "▶ Chay mo phong");
        });

        Label lblRate = ctxLabel("Toc do (xe/phut):");
        Slider sliderRate = new Slider(0, 60, 30);
        sliderRate.setPrefWidth(100);
        Label lblRateVal = ctxLabel("30");
        sliderRate.valueProperty().addListener((ob, ov, nv) -> {
            spawner.setSpawnRatePerMinute(nv.doubleValue());
            lblRateVal.setText(String.valueOf((int) nv.doubleValue()));
        });
        spawner.setSpawnRatePerMinute(30);

        Label lblCar  = ctxLabel("Oto:");
        Slider sCar   = new Slider(0, 1, 0.5);
        Label lblMoto = ctxLabel("Xemay:");
        Slider sMoto  = new Slider(0, 1, 0.4);
        Label lblBike = ctxLabel("Xedap:");
        Slider sBike  = new Slider(0, 1, 0.1);
        for (Slider s : new Slider[]{sCar, sMoto, sBike}) s.setPrefWidth(55);

        sCar.valueProperty().addListener((ob, ov, nv) ->
                spawner.setVehicleRatio(com.myteam.traffic.vehicle.VehicleType.CAR, nv.doubleValue()));
        sMoto.valueProperty().addListener((ob, ov, nv) ->
                spawner.setVehicleRatio(com.myteam.traffic.vehicle.VehicleType.MOTORBIKE, nv.doubleValue()));
        sBike.valueProperty().addListener((ob, ov, nv) ->
                spawner.setVehicleRatio(com.myteam.traffic.vehicle.VehicleType.BICYCLE, nv.doubleValue()));

        Label lblAgg = ctxLabel("Lai au:");
        Slider sAgg  = new Slider(0, 1, 0.3);
        sAgg.setPrefWidth(55);
        sAgg.valueProperty().addListener((ob, ov, nv) ->
                spawner.setAggressiveRatio(nv.doubleValue()));

        Label lblManual = ctxLabel("Them xe:");
        ComboBox<com.myteam.traffic.vehicle.VehicleType> typeBox = new ComboBox<>();
        typeBox.getItems().addAll(
                com.myteam.traffic.vehicle.VehicleType.CAR,
                com.myteam.traffic.vehicle.VehicleType.MOTORBIKE,
                com.myteam.traffic.vehicle.VehicleType.BICYCLE,
                com.myteam.traffic.vehicle.VehicleType.AMBULANCE,
                com.myteam.traffic.vehicle.VehicleType.FIRETRUCK
        );
        typeBox.setValue(com.myteam.traffic.vehicle.VehicleType.CAR);
        typeBox.setStyle("-fx-background-color:#2a3550;");
        styleVehicleTypeCombo(typeBox);

        CheckBox cbAggOne = styledCb("Lai au", false, e -> {});

        Button btnAddOne = actionBtn("+ Them xe", "Them 1 xe ngay theo loai da chon");
        btnAddOne.setOnAction(e -> {
            spawner.spawnManual(typeBox.getValue(), cbAggOne.isSelected());
            if (!view.isSimulationRunning()) view.stepOnce();
        });

        HBox box = hbox(btnPlay,
                vsep(), lblRate, sliderRate, lblRateVal,
                vsep(), lblCar, sCar, lblMoto, sMoto, lblBike, sBike,
                vsep(), lblAgg, sAgg,
                vsep(), lblManual, typeBox, cbAggOne, btnAddOne);
        box.setSpacing(6);
        return box;
    }

    private void styleVehicleTypeCombo(ComboBox<com.myteam.traffic.vehicle.VehicleType> box) {
        box.setButtonCell(new javafx.scene.control.ListCell<>() {
            @Override protected void updateItem(com.myteam.traffic.vehicle.VehicleType item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.toString());
                setStyle("-fx-text-fill:white; -fx-font-weight:bold; -fx-font-size:12px; -fx-background-color:transparent;");
            }
        });
        box.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
            @Override protected void updateItem(com.myteam.traffic.vehicle.VehicleType item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(""); return; }
                setText(item.toString());
                setStyle("-fx-text-fill:#c8d0e8; -fx-font-size:12px; -fx-background-color:transparent;");
            }
        });
    }

    public static void main(String[] args) { launch(args); }
}