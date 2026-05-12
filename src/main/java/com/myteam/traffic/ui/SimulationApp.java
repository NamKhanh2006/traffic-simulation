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

/**
 * SimulationApp — Entry point với UI 2 tầng mở rộng được.
 *
 *  ┌──────────────────────────────────────────────────────────────────┐
 *  │ TOP BAR  │ [🖐 Di chuyển] [🛣 Vẽ đường] [✏ Sửa vạch] [...]    │
 *  │          │  🔍+ 🔎-   ↩ Hoàn tác   🗑 Xóa    Lưới  Nhãn  Hint  │
 *  ├──────────────────────────────────────────────────────────────────┤
 *  │ CONTEXT  │  (tự thay đổi theo mode đang chọn)                   │
 *  │  BAR     │  PAN  : hướng dẫn di chuyển                          │
 *  │          │  DRAW : Số làn  •  Tạo nút giao  •  tip              │
 *  │          │  EDIT : hướng dẫn hover vạch                         │
 *  └──────────────────────────────────────────────────────────────────┘
 *
 *  Thêm mode mới: tạo modeBtn() + buildCtxXxx() + thêm vào ctxStack.
 */
public class SimulationApp extends Application {

    // ── Lane factories ────────────────────────────────────────
    private static Lane fwdLane(int idx, double w) {
        return new Lane(idx, Lane.Direction.FORWARD, w,
                EnumSet.of(Lane.VehicleCategory.CAR, Lane.VehicleCategory.MOTORBIKE),
                EnumSet.of(Lane.Movement.STRAIGHT),
                Lane.MarkingType.DASHED, Lane.MarkingType.DASHED);
    }

    private static Lane bwdLane(int idx, double w) {
        return new Lane(idx, Lane.Direction.BACKWARD, w,
                EnumSet.of(Lane.VehicleCategory.CAR, Lane.VehicleCategory.BUS),
                EnumSet.of(Lane.Movement.STRAIGHT),
                Lane.MarkingType.DASHED, Lane.MarkingType.DASHED);
    }

    public static List<Lane> createLanes(int totalLanes) {
        List<Lane> lanes = new ArrayList<>();
        if (totalLanes == 1) {
            // 1 làn: chỉ chiều xuôi
            lanes.add(fwdLane(0, 3.5));
            return lanes;
        }
        int fwd = (totalLanes + 1) / 2; // số làn xuôi (nhiều hơn nếu lẻ)
        int bwd = totalLanes / 2;       // số làn ngược
        for (int i = 0; i < fwd; i++) lanes.add(fwdLane(i, 3.5));
        for (int i = 0; i < bwd; i++) lanes.add(bwdLane(fwd + i, 3.5));
        return lanes;
    }

    // ── Start ─────────────────────────────────────────────────
    @Override
    public void start(Stage stage) {
        SimulationView view = new SimulationView(1200, 750);
        RoadNetwork network = new RoadNetwork();
        view.setNetwork(network);

        // ════ TOP BAR ════════════════════════════════════════
        ToggleButton btnModePan  = modeBtn("🖐", "Di chuyển",  "Kéo và xoay bản đồ");
        ToggleButton btnModeDraw = modeBtn("🛣", "Vẽ đường",   "Vẽ đoạn đường mới");
        ToggleButton btnModeEdit = modeBtn("✏️", "Sửa vạch",   "Hover vạch kẻ → menu chọn loại");
        // [THÊM MODE MỚI: tạo thêm ToggleButton ở đây, thêm vào modeGroup và modeBox]

        ToggleGroup modeGroup = new ToggleGroup();
        btnModePan.setToggleGroup(modeGroup);
        btnModeDraw.setToggleGroup(modeGroup);
        btnModeEdit.setToggleGroup(modeGroup);
        btnModePan.setSelected(true);

        HBox modeBox = new HBox(4, btnModePan, btnModeDraw, btnModeEdit);
        modeBox.setAlignment(Pos.CENTER_LEFT);

        Button btnZoomIn  = actionBtn("🔍+", "Phóng to  (Scroll)");
        Button btnZoomOut = actionBtn("🔎-", "Thu nhỏ  (Scroll)");
        Button btnUndo    = actionBtn("↩ Hoàn tác", "Ctrl + Z");
        Button btnClear   = actionBtn("🗑 Xóa sạch", "Xóa toàn bộ");

        btnZoomIn .setOnAction(e -> view.zoomIn());
        btnZoomOut.setOnAction(e -> view.zoomOut());
        btnUndo   .setOnAction(e -> view.undo());
        btnClear  .setOnAction(e -> { view.saveSnapshot(); view.setNetwork(new RoadNetwork()); });

        CheckBox cbGrid   = styledCb("Lưới",       true, e -> view.setShowGrid(((CheckBox)e.getSource()).isSelected()));
        CheckBox cbLabels = styledCb("Nhãn đường", true, e -> view.setShowLabels(((CheckBox)e.getSource()).isSelected()));

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
        HBox ctxPan  = buildCtxPan();
        HBox ctxDraw = buildCtxDraw(view);
        HBox ctxEdit = buildCtxEdit();
        // [THÊM MODE MỚI: thêm buildCtxXxx() và thêm vào ctxStack]

        StackPane ctxStack = new StackPane(ctxPan, ctxDraw, ctxEdit);
        ctxPan.setVisible(true);
        ctxDraw.setVisible(false);
        ctxEdit.setVisible(false);
        StackPane.setAlignment(ctxPan,  Pos.CENTER_LEFT);
        StackPane.setAlignment(ctxDraw, Pos.CENTER_LEFT);
        StackPane.setAlignment(ctxEdit, Pos.CENTER_LEFT);
        ctxStack.setPadding(new Insets(5, 14, 5, 14));
        ctxStack.setStyle("-fx-background-color:#1a2030; -fx-border-color:#252f45; -fx-border-width:1 0 0 0;");
        ctxStack.setMinHeight(34);

        modeGroup.selectedToggleProperty().addListener((obs, old, nw) -> {
            ctxPan .setVisible(nw == btnModePan);
            ctxDraw.setVisible(nw == btnModeDraw);
            ctxEdit.setVisible(nw == btnModeEdit);
            if (nw == btnModePan)  view.setInteractionType(SimulationView.InteractionType.PAN);
            if (nw == btnModeDraw) view.setInteractionType(SimulationView.InteractionType.DRAW_ROAD);
            if (nw == btnModeEdit) view.setInteractionType(SimulationView.InteractionType.EDIT_MARKINGS);
        });

        // ════ LAYOUT CHÍNH ═══════════════════════════════════
        VBox header = new VBox(topBar, ctxStack);

        StackPane canvasHolder = new StackPane(view);
        canvasHolder.setStyle("-fx-background-color:#1a1f2e;");
        view.widthProperty().bind(canvasHolder.widthProperty());
        view.heightProperty().bind(canvasHolder.heightProperty());

        BorderPane root = new BorderPane();
        root.setTop(header);
        root.setCenter(canvasHolder);

        // --- TÌM VÀ THAY THẾ KHỐI NÀY TRONG SimulationApp.java ---

        Scene scene = new Scene(root, 1280, 850);
        scene.getStylesheets().add(
                "data:text/css," +
                        ".check-box .text{-fx-fill:#c8d0e8;}" +
                        ".label{-fx-text-fill:#c8d0e8;}" +
                        // THÊM CSS NÀY ĐỂ FIX LỖI CHỮ MỜ TRONG COMBOBOX
                        ".combo-box-popup .list-view { -fx-background-color: #1a2030; -fx-border-color: #3a4560; }" +
                        ".combo-box-popup .list-cell { -fx-text-fill: #c8d0e8; -fx-padding: 4 8 4 8; }" +
                        ".combo-box-popup .list-cell:hover { -fx-background-color: #3d4a5c; }"
        );
        scene.setOnKeyPressed(e -> { if (e.isControlDown() && e.getCode() == KeyCode.Z) view.undo(); });

        stage.setTitle("Traffic Builder — Trình Dựng Lưới Giao Thông");
        stage.setScene(scene);
        stage.show();
        view.resetView();
    }

    // ════════════════════════════════════════════════════════
    // CONTEXT BAR BUILDERS — thêm mode mới → thêm hàm ở đây
    // ════════════════════════════════════════════════════════

    private HBox buildCtxPan() {
        Label lbl = new Label("🖐  Di chuyển — Giữ chuột PHẢI để kéo bản đồ, cuộn để zoom.");
        lbl.setStyle("-fx-text-fill:#5a7090; -fx-font-size:11px;");
        return hbox(lbl);
    }

    private HBox buildCtxDraw(SimulationView view) {
        Label lblLane = new Label("Số làn:");
        lblLane.setStyle("-fx-text-fill:#c8d0e8; -fx-font-size:12px;");

        ComboBox<String> laneBox = new ComboBox<>();
        laneBox.getItems().addAll(
                "1 làn", "2 làn", "3 làn", "4 làn", "5 làn", "6 làn", "7 làn", "8 làn",
                "── Vòng xuyến ──",
                "🔄 Vòng xuyến nhỏ", "🔄 Vòng xuyến lớn"
        );
        laneBox.setValue("4 làn");
        laneBox.setPrefWidth(130);
        laneBox.setStyle("-fx-background-color:#2a3550; -fx-text-fill:white;");
        laneBox.setButtonCell(new javafx.scene.control.ListCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item);
                setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 12px; -fx-background-color: transparent;");
            }
        });
        laneBox.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(""); return; }
                setText(item);
                boolean isSep = item.startsWith("──");
                setDisable(isSep);
                setStyle(isSep
                        ? "-fx-text-fill:#4a6080; -fx-font-size:11px; -fx-background-color:transparent;"
                        : "-fx-text-fill:#c8d0e8; -fx-font-size:12px; -fx-background-color:transparent;");
            }
        });
        view.setCurrentLaneConfig(4);
        laneBox.setOnAction(e -> {
            String val = laneBox.getValue();
            if (val == null || val.startsWith("──")) return;
            if (val.contains("nhỏ"))  { view.setCurrentLaneConfig(9);  return; }
            if (val.contains("lớn"))  { view.setCurrentLaneConfig(10); return; }
            try { view.setCurrentLaneConfig(Integer.parseInt(val.split(" ")[0])); }
            catch (NumberFormatException ex) { /* ignore */ }
        });

        CheckBox cbAuto = styledCb("Tự tạo nút giao khi cắt đường", true,
                e -> view.setAutoIntersect(((CheckBox) e.getSource()).isSelected()));

        Label tip = new Label("💡 Click = điểm đầu  •  Kéo rê = điểm cuối  •  Ctrl+Z = undo");
        tip.setStyle("-fx-text-fill:#4a6080; -fx-font-size:11px;");

        HBox box = hbox(lblLane, laneBox, vsep(), cbAuto, vsep(), tip);
        box.setSpacing(10);
        return box;
    }

    private HBox buildCtxEdit() {
        Label tip = new Label(
                "✏️  Hover chuột lên ranh giới giữa 2 làn → vạch sáng lên  •  " +
                        "Chuột PHẢI lên vạch đó → chọn loại: Nét đứt  /  Nét liền  /  Nét đôi  /  Không vạch"
        );
        tip.setStyle("-fx-text-fill:#c8d0e8; -fx-font-size:11px;");
        return hbox(tip);
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

    private Separator vsep() {
        Separator s = new Separator(Orientation.VERTICAL);
        s.setPadding(new Insets(0, 3, 0, 3));
        return s;
    }

    private HBox hbox(javafx.scene.Node... nodes) {
        HBox b = new HBox(8, nodes);
        b.setAlignment(Pos.CENTER_LEFT);
        return b;
    }

    public static void main(String[] args) { launch(args); }
}