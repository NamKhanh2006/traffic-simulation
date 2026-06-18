package com.myteam.traffic.ui;

import com.myteam.traffic.light.TrafficLight;
import com.myteam.traffic.light.TrafficLightState;
import javafx.geometry.VPos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import java.util.Optional;

/**
 * TrafficLightRenderer chịu trách nhiệm vẽ các đèn giao thông lên JavaFX canvas.
 * 
 * <p>Renderer này tách biệt hoàn toàn logic hiển thị khỏi logic trạng thái của đèn.
 * Nó sử dụng {@link TrafficLight#getCountdownDisplay()} để quyết định có vẽ
 * bộ đếm ngược hay không, giúp hiện thực hóa các loại đèn khác nhau một cách nhất quán.</p>
 */
public class TrafficLightRenderer {

    private final SimulationView view;

    // ── Bảng màu ──────────────────────────────────────────────
    private static final Color COLOR_HOUSING = Color.web("#222222");
    private static final Color COLOR_RED     = Color.web("#ff3b30");
    private static final Color COLOR_YELLOW  = Color.web("#ffcc00");
    private static final Color COLOR_GREEN   = Color.web("#34c759");
    private static final Color COLOR_OFF     = Color.web("#1a1a1a");
    private static final Color COLOR_TEXT    = Color.web("#ffffff");
    private static final Color COLOR_TEXT_BG = Color.web("#000000", 0.75);

    public TrafficLightRenderer(SimulationView view) {
        this.view = view;
    }

    /**
     * Vẽ một đèn giao thông tại vị trí tọa độ thế giới (world coordinates).
     *
     * @param gc     GraphicsContext của canvas.
     * @param light  Đối tượng TrafficLight cần vẽ.
     * @param worldX Tọa độ X trong thế giới mô phỏng.
     * @param worldY Tọa độ Y trong thế giới mô phỏng.
     */
    public void draw(GraphicsContext gc, TrafficLight light, double worldX, double worldY) {
        double scale = view.getViewScale();
        if (scale < 0.2) return; // Không vẽ nếu quá nhỏ

        double screenX = view.toScreenX(worldX);
        double screenY = view.toScreenY(worldY);

        // Kích thước cơ bản (tính theo world units rồi scale lên)
        double w = 14 * scale;
        double h = 36 * scale;
        double r = 4 * scale;
        double bulbD = 8 * scale;

        gc.save();

        // 1. Vẽ vỏ đèn (Housing)
        gc.setFill(COLOR_HOUSING);
        gc.fillRoundRect(screenX - w / 2, screenY - h / 2, w, h, r, r);

        // 2. Vẽ các bóng đèn (Bulbs)
        TrafficLightState state = light.getCurrentState();

        // Đèn đỏ (Trên cùng)
        gc.setFill(state == TrafficLightState.RED ? COLOR_RED : COLOR_OFF);
        gc.fillOval(screenX - bulbD / 2, screenY - h / 2 + 4 * scale, bulbD, bulbD);

        // Đèn vàng (Giữa)
        gc.setFill(state == TrafficLightState.YELLOW ? COLOR_YELLOW : COLOR_OFF);
        gc.fillOval(screenX - bulbD / 2, screenY - bulbD / 2, bulbD, bulbD);

        // Đèn xanh (Dưới cùng)
        gc.setFill(state == TrafficLightState.GREEN ? COLOR_GREEN : COLOR_OFF);
        gc.fillOval(screenX - bulbD / 2, screenY + h / 2 - bulbD - 4 * scale, bulbD, bulbD);

        // 3. Vẽ đếm ngược (nếu có giá trị từ Optional)
        Optional<Integer> countdown = light.getCountdownDisplay();
        if (countdown.isPresent() && scale > 0.4) {
            drawCountdownValue(gc, countdown.get(), screenX, screenY - h / 2 - 10 * scale, scale);
        }

        gc.restore();
    }

    private void drawCountdownValue(GraphicsContext gc, int seconds, double x, double y, double scale) {
        String text = String.valueOf(seconds);
        double fontSize = Math.max(9, 13 * scale);
        gc.setFont(Font.font("Monospace", FontWeight.BOLD, fontSize));
        
        double padding = 3 * scale;
        double textW = text.length() * fontSize * 0.65;
        double boxW = textW + padding * 2;
        double boxH = fontSize + padding;

        gc.setFill(COLOR_TEXT_BG);
        gc.fillRoundRect(x - boxW / 2, y - boxH, boxW, boxH, 4 * scale, 4 * scale);

        gc.setFill(COLOR_TEXT);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.BOTTOM);
        gc.fillText(text, x, y - padding / 2);
    }
}