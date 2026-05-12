package com.myteam.traffic.ui;

import com.myteam.traffic.model.infrastructure.*;
import com.myteam.traffic.model.infrastructure.intersection.*;
import javafx.geometry.VPos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

import java.util.List;

/**
 * InfrastructureRenderer — Đã cập nhật để tương thích với SimulationView mới.
 */
public class InfrastructureRenderer {

    private final SimulationView view;

    // ── Bảng màu ──────────────────────────────────────────────
    public  static final Color COLOR_BG        = Color.web("#1a1f2e");
    private static final Color COLOR_HIGHWAY   = Color.web("#283444");
    private static final Color COLOR_ROAD      = Color.web("#3d4a5c");
    private static final Color COLOR_RAMP      = Color.web("#3d4a5c");
    private static final Color COLOR_JUNCTION  = Color.web("#3d4a5c");
    private static final Color COLOR_ISLAND    = Color.web("#212d3a");
    private static final Color COLOR_ISLAND_RING= Color.web("#2e3d50");
    private static final Color COLOR_MARK_YELLOW= Color.web("#e8d44d");
    private static final Color COLOR_MARK_WHITE = Color.web("#e0e8f0");
    private static final Color COLOR_GRID      = Color.web("#252b3d");
    private static final Color COLOR_GRID_LABEL= Color.web("#3a4560");
    private static final Color COLOR_LABEL     = Color.web("#d4ddf0");
    private static final Color COLOR_LABEL_BG  = Color.web("#1a1f2e", 0.88);
    private static final Color COLOR_EMERGENCY_LANE = Color.web("#ff6b35", 0.25);

    private static final double LANE_PX = 20.0;

    public InfrastructureRenderer(SimulationView view) {
        this.view = view;
    }

    // ════════════════════════════════════════════════════════════
    // 0. LƯỚI TỌA ĐỘ
    // ════════════════════════════════════════════════════════════
    public void drawGrid(GraphicsContext gc, double canvasW, double canvasH) {
        // Cập nhật: Sử dụng getViewScale, getViewX, getViewY
        double scale = view.getViewScale();
        double tx = view.getViewX(), ty = view.getViewY();

        if (scale * 25 > 12) {
            gc.setStroke(Color.web("#222840", 0.6));
            gc.setLineWidth(0.5);
            drawGridLines(gc, canvasW, canvasH, 25, tx, ty, scale);
        }

        gc.setStroke(COLOR_GRID);
        gc.setLineWidth(1.0);
        drawGridLines(gc, canvasW, canvasH, 100, tx, ty, scale);

        if (scale > 0.25) {
            gc.setFont(Font.font("Monospace", Math.max(9, 10 * scale)));
            gc.setFill(COLOR_GRID_LABEL);
            gc.setTextAlign(TextAlignment.LEFT);
            gc.setTextBaseline(VPos.TOP);

            double step = 100 * scale;
            double startWorldX = Math.ceil(-tx / scale / 100) * 100;
            double startWorldY = Math.ceil(-ty / scale / 100) * 100;

            for (double wx = startWorldX; wx * scale + tx < canvasW + step; wx += 100) {
                for (double wy = startWorldY; wy * scale + ty < canvasH + step; wy += 100) {
                    double sx = wx * scale + tx, sy = wy * scale + ty;
                    if (sx > -20 && sx < canvasW && sy > -20 && sy < canvasH) {
                        gc.fillText(String.format("(%d,%d)", (int)wx, (int)wy), sx + 3, sy + 2);
                    }
                }
            }
        }

        double ox = tx, oy = ty;
        gc.setStroke(Color.web("#3a4a70", 0.9));
        gc.setLineWidth(1.5);
        if (oy > 0 && oy < canvasH) gc.strokeLine(0, oy, canvasW, oy);
        if (ox > 0 && ox < canvasW) gc.strokeLine(ox, 0, ox, canvasH);
    }

    private void drawGridLines(GraphicsContext gc, double cw, double ch, double worldStep, double tx, double ty, double scale) {
        double step = worldStep * scale;
        double startX = tx % step;
        double startY = ty % step;
        for (double x = startX; x < cw; x += step) gc.strokeLine(x, 0, x, ch);
        for (double y = startY; y < ch; y += step) gc.strokeLine(0, y, cw, y);
    }

    // ════════════════════════════════════════════════════════════
    // 1. THÂN ĐƯỜNG
    // ════════════════════════════════════════════════════════════
    public void drawRoadBody(GraphicsContext gc, RoadSegment seg) {
        double scale = view.getViewScale();
        double w = roadWidth(seg, scale);
        gc.save();

        if (seg instanceof HighwayRampSegment ramp) {
            double[] b = bezier(ramp);
            gc.setStroke(COLOR_RAMP);
            gc.setLineWidth(w);
            gc.setLineCap(StrokeLineCap.BUTT);
            gc.setLineJoin(StrokeLineJoin.ROUND);
            gc.beginPath();
            gc.moveTo(b[0], b[1]);
            gc.bezierCurveTo(b[4], b[5], b[6], b[7], b[2], b[3]);
            gc.stroke();
        } else {
            double sx = view.toScreenX(seg.getStartX()), sy = view.toScreenY(seg.getStartY());
            double ex = view.toScreenX(seg.getEndX()),   ey = view.toScreenY(seg.getEndY());
            Color c = (seg instanceof HighwaySegment) ? COLOR_HIGHWAY : COLOR_ROAD;
            gc.setStroke(c);
            gc.setLineWidth(w);
            gc.setLineCap(StrokeLineCap.BUTT);
            gc.strokeLine(sx, sy, ex, ey);

            if (seg instanceof HighwaySegment hwy && hwy.hasEmergencyLane()) {
                drawEmergencyLaneHighlight(gc, hwy, scale);
            }
        }
        gc.restore();
    }

    private void drawEmergencyLaneHighlight(GraphicsContext gc, HighwaySegment hwy, double scale) {
        Lane lastLane = hwy.getLanes().get(hwy.getLanes().size() - 1);
        double laneW   = lastLane.getWidth() * scale * 5;
        double totalW  = roadWidth(hwy, scale);
        double offset  = totalW / 2.0 - laneW / 2.0;

        double sx = view.toScreenX(hwy.getStartX()), sy = view.toScreenY(hwy.getStartY());
        double ex = view.toScreenX(hwy.getEndX()),   ey = view.toScreenY(hwy.getEndY());
        double ang = Math.atan2(ey - sy, ex - sx);
        double perpX = Math.cos(ang + Math.PI/2), perpY = Math.sin(ang + Math.PI/2);

        gc.save();
        gc.setStroke(COLOR_EMERGENCY_LANE);
        gc.setLineWidth(laneW);
        gc.setLineCap(StrokeLineCap.BUTT);
        gc.strokeLine(sx + perpX*offset, sy + perpY*offset, ex + perpX*offset, ey + perpY*offset);
        gc.restore();
    }

    // ════════════════════════════════════════════════════════════
    // 2. FILL NÚT GIAO
    // ════════════════════════════════════════════════════════════
    public void drawJunctionFill(GraphicsContext gc, IntersectionRenderData inter) {
        double scale = view.getViewScale();
        double scx = view.toScreenX(inter.centerX);
        double scy = view.toScreenY(inter.centerY);

        // Vòng xuyến → vẽ kiểu đặc biệt với đảo tròn trung tâm
        if (inter.typeName.startsWith("Vòng xuyến")) {
            drawRoundaboutFill(gc, inter, scx, scy, scale);
            return;
        }

        if (inter.arms.isEmpty()) return;

        // Tính bán kính vùng giao lộ = nửa chiều rộng nhánh lớn nhất (theo world units × scale)
        double maxHalfWidth = 0;
        Color fillColor = COLOR_ROAD;
        for (IntersectionRenderData.ArmData arm : inter.arms) {
            // arm.totalWidth là tổng world-unit width (số làn × 3.5m)
            double hw = arm.totalWidth * scale; // 1 world unit = 1 screen pixel tại scale=1
            maxHalfWidth = Math.max(maxHalfWidth, hw);
            if (arm.segment instanceof HighwaySegment) fillColor = COLOR_HIGHWAY;
        }
        double junctionR = Math.max(maxHalfWidth * 0.7, 8 * scale);

        gc.save();
        gc.setFill(fillColor);

        // Bước 1: hình tròn nền
        gc.fillOval(scx - junctionR, scy - junctionR, junctionR * 2, junctionR * 2);

        // Bước 2: lưỡi nối từng nhánh vào hình tròn
        for (IntersectionRenderData.ArmData arm : inter.arms) {
            double hw = arm.totalWidth * scale * 0.5;
            double ang = Math.toRadians(arm.approachAngleDeg);
            double dirX = Math.cos(ang), dirY = Math.sin(ang);
            double perpX = -dirY, perpY = dirX;

            double[] px = {
                    scx + perpX * hw,  scx - perpX * hw,
                    scx + dirX * junctionR * 1.4 - perpX * hw,
                    scx + dirX * junctionR * 1.4 + perpX * hw
            };
            double[] py = {
                    scy + perpY * hw,  scy - perpY * hw,
                    scy + dirY * junctionR * 1.4 - perpY * hw,
                    scy + dirY * junctionR * 1.4 + perpY * hw
            };
            gc.fillPolygon(px, py, 4);
        }

        // Bước 3: vẽ lại tròn tâm để bo góc
        gc.fillOval(scx - junctionR, scy - junctionR, junctionR * 2, junctionR * 2);
        gc.restore();
    }

    /** Vẽ vòng xuyến với đảo tròn trung tâm + lane tròn bao quanh */
    private void drawRoundaboutFill(GraphicsContext gc, IntersectionRenderData inter,
                                    double scx, double scy, double scale) {
        double outerR = inter.radius * scale;
        double ringW  = LANE_PX * scale * 1.2;
        double islandR = Math.max(6 * scale, outerR - ringW);

        gc.save();
        gc.setFill(COLOR_ISLAND_RING);
        gc.fillOval(scx - outerR, scy - outerR, outerR * 2, outerR * 2);

        gc.setFill(COLOR_ISLAND);
        gc.fillOval(scx - islandR, scy - islandR, islandR * 2, islandR * 2);

        gc.setStroke(Color.web("#4a6040", 0.7));
        gc.setLineWidth(Math.max(0.8, 1.2 * scale));
        gc.strokeOval(scx - islandR, scy - islandR, islandR * 2, islandR * 2);

        gc.setFill(COLOR_ISLAND_RING);
        for (IntersectionRenderData.ArmData arm : inter.arms) {
            double hw = arm.totalWidth * scale * 0.5;
            double ang = Math.toRadians(arm.approachAngleDeg);
            double dirX = Math.cos(ang), dirY = Math.sin(ang);
            double perpX = -dirY, perpY = dirX;
            double[] px = {
                    scx + perpX * hw,  scx - perpX * hw,
                    scx + dirX * outerR * 1.3 - perpX * hw,
                    scx + dirX * outerR * 1.3 + perpX * hw
            };
            double[] py = {
                    scy + perpY * hw,  scy - perpY * hw,
                    scy + dirY * outerR * 1.3 - perpY * hw,
                    scy + dirY * outerR * 1.3 + perpY * hw
            };
            gc.fillPolygon(px, py, 4);
        }
        gc.setFill(COLOR_ISLAND);
        gc.fillOval(scx - islandR, scy - islandR, islandR * 2, islandR * 2);

        // --- SỬA: THÊM ĐOẠN NÀY ĐỂ VẼ VẠCH CHẠY VÒNG QUANH ĐẢO ---
        if (scale > 0.2) {
            double markR = (outerR + islandR) / 2.0; // Bán kính vạch ở giữa lòng đường
            gc.setStroke(COLOR_MARK_WHITE);
            gc.setLineWidth(Math.max(1.0, 1.5 * scale));
            gc.setLineDashes(12 * scale, 10 * scale);
            gc.strokeOval(scx - markR, scy - markR, markR * 2, markR * 2);
            gc.setLineDashes(null);
        }
        // ---------------------------------------------------------

        gc.restore();
    }

    /** @deprecated dùng drawJunctionFill(gc, IntersectionRenderData) */
    public void drawJunctionFill(GraphicsContext gc, double cx, double cy, List<RoadSegment> allSegments) {
        // No-op
    }

    // ════════════════════════════════════════════════════════════
    // 3. VÒNG XUYẾN
    // ════════════════════════════════════════════════════════════
    public void drawRoundabout(GraphicsContext gc, IntersectionRenderData data) {
        double scale = view.getViewScale();
        double cx = view.toScreenX(data.centerX), cy = view.toScreenY(data.centerY);

        double outerR = data.radius * scale;
        double ringW  = LANE_PX * scale * 1.5;
        double islandR = Math.max(6 * scale, outerR - ringW);

        gc.save();
        gc.setFill(COLOR_ISLAND_RING);
        gc.fillOval(cx - outerR, cy - outerR, outerR*2, outerR*2);
        gc.setFill(COLOR_ISLAND);
        gc.fillOval(cx - islandR, cy - islandR, islandR*2, islandR*2);

        if (scale > 0.3) {
            double markR = (outerR + islandR) / 2;
            gc.setStroke(COLOR_MARK_YELLOW);
            gc.setLineWidth(Math.max(0.7, 1.5 * scale));
            gc.setLineDashes(10 * scale, 8 * scale);
            gc.strokeOval(cx - markR, cy - markR, markR*2, markR*2);
        }
        gc.restore();
    }

    // ════════════════════════════════════════════════════════════
    // 4. VẠCH KẺ ĐƯỜNG
    // ════════════════════════════════════════════════════════════
    // ════════════════════════════════════════════════════════════
    // 4. VẠCH KẺ ĐƯỜNG
    // ════════════════════════════════════════════════════════════
    public void drawRoadMarks(GraphicsContext gc, RoadSegment seg, double rStartWorld, double rEndWorld) {
        if (seg.isConnector()) return;
        double scale = view.getViewScale();
        if (scale < 0.18) return;

        gc.save();
        if (seg instanceof HighwayRampSegment ramp) {
            drawRampCenterLine(gc, ramp, scale);
        } else {
            double sx = view.toScreenX(seg.getStartX()), sy = view.toScreenY(seg.getStartY());
            double ex = view.toScreenX(seg.getEndX()),   ey = view.toScreenY(seg.getEndY());
            double ang = Math.atan2(ey - sy, ex - sx);

            // SỬA: Cắt bỏ vạch kẻ vừa đúng bằng bán kính Intersection + 2 pixel padding
            double offS = (rStartWorld > 0) ? (rStartWorld * scale + 2 * scale) : 8 * scale;
            double offE = (rEndWorld > 0)   ? (rEndWorld * scale + 2 * scale)   : 8 * scale;

            double startOffsetX = sx + Math.cos(ang) * offS;
            double startOffsetY = sy + Math.sin(ang) * offS;
            double endOffsetX   = ex - Math.cos(ang) * offE;
            double endOffsetY   = ey - Math.sin(ang) * offE;

            drawCenterLine(gc, seg, startOffsetX, startOffsetY, endOffsetX, endOffsetY, ang, scale);
            drawEdgeLines(gc, seg, sx, sy, ex, ey, ang, offS, offE, scale);
            drawLaneDividers(gc, seg, startOffsetX, startOffsetY, endOffsetX, endOffsetY, ang, scale);

            // Chỉ vẽ vạch dừng xe nếu có Intersection ở đầu đó
            if (rStartWorld > 0) drawStopLine(gc, startOffsetX, startOffsetY, ang, seg, scale);
            if (rEndWorld > 0)   drawStopLine(gc, endOffsetX, endOffsetY, ang + Math.PI, seg, scale);
        }
        gc.restore();
    }
    // Thay thế hàm drawLaneDividers cũ bằng đoạn này:
    private void drawLaneDividers(GraphicsContext gc, RoadSegment seg, double sx, double sy, double ex, double ey, double ang, double scale) {
        List<Lane> lanes = seg.getLanes();
        if (lanes.size() <= 1) return;

        double totalW = roadWidth(seg, scale);
        double perpX = Math.cos(ang + Math.PI/2), perpY = Math.sin(ang + Math.PI/2);
        double currentOff = -totalW / 2.0;

        for (int i = 0; i < lanes.size() - 1; i++) {
            Lane lane = lanes.get(i);
            currentOff += lane.getWidth() * LANE_PX * scale / 3.5;

            Lane.MarkingType marking = lane.getRightMarking();
            if (marking == Lane.MarkingType.NONE) continue;

            // SỬA LỖI MÀU SẮC: Trắng hay Vàng phụ thuộc vào Loại vạch bạn chọn
            Color baseColor = COLOR_MARK_WHITE;

            if (marking == Lane.MarkingType.YELLOW_SOLID || marking == Lane.MarkingType.DOUBLE_SOLID) {
                baseColor = COLOR_MARK_YELLOW;
            } else if (marking == Lane.MarkingType.SOLID) {
                baseColor = COLOR_MARK_WHITE; // Ép buộc Trắng nếu người dùng chọn Trắng
            } else if (lane.getDirection() != lanes.get(i+1).getDirection()) {
                baseColor = COLOR_MARK_YELLOW; // Mặc định ở giữa (Nét đứt ngược chiều) là Vàng
            }

            gc.setStroke(baseColor);

            double startX = sx + perpX*currentOff;
            double startY = sy + perpY*currentOff;
            double endX   = ex + perpX*currentOff;
            double endY   = ey + perpY*currentOff;

            switch (marking) {
                case DASHED:
                    gc.setLineWidth(Math.max(0.6, 1.2 * scale));
                    gc.setLineDashes(10*scale, 12*scale);
                    gc.strokeLine(startX, startY, endX, endY);
                    break;
                case SOLID:
                case YELLOW_SOLID:
                    gc.setLineWidth(Math.max(0.6, 1.2 * scale));
                    gc.setLineDashes(null);
                    gc.strokeLine(startX, startY, endX, endY);
                    break;
                case DOUBLE_SOLID:
                    gc.setLineWidth(Math.max(0.4, 0.8 * scale));
                    gc.setLineDashes(null);
                    double dOffX = Math.cos(ang + Math.PI/2) * (2 * scale);
                    double dOffY = Math.sin(ang + Math.PI/2) * (2 * scale);
                    gc.strokeLine(startX - dOffX, startY - dOffY, endX - dOffX, endY - dOffY);
                    gc.strokeLine(startX + dOffX, startY + dOffY, endX + dOffX, endY + dOffY);
                    break;
                case LEFT_DASHED_RIGHT_SOLID:
                case LEFT_SOLID_RIGHT_DASHED:
                    double offX = Math.cos(ang + Math.PI/2) * (1.5 * scale);
                    double offY = Math.sin(ang + Math.PI/2) * (1.5 * scale);
                    gc.setLineWidth(Math.max(0.4, 0.8 * scale));

                    // Nét liền
                    gc.setLineDashes(null);
                    if (marking == Lane.MarkingType.LEFT_SOLID_RIGHT_DASHED)
                        gc.strokeLine(startX - offX, startY - offY, endX - offX, endY - offY);
                    else gc.strokeLine(startX + offX, startY + offY, endX + offX, endY + offY);

                    // Nét đứt
                    gc.setLineDashes(10*scale, 12*scale);
                    if (marking == Lane.MarkingType.LEFT_SOLID_RIGHT_DASHED)
                        gc.strokeLine(startX + offX, startY + offY, endX + offX, endY + offY);
                    else gc.strokeLine(startX - offX, startY - offY, endX - offX, endY - offY);
                    break;
                default: break;
            }
        }
        gc.setLineDashes(null);
    }



    // drawLaneDividers handles center line (yellow) via direction-change detection.
    // This method is intentionally empty to avoid double-drawing.
    private void drawCenterLine(GraphicsContext gc, RoadSegment seg,
                                double startX, double startY, double endX, double endY,
                                double ang, double scale) {
        // No-op: yellow center marking is drawn by drawLaneDividers
    }

    private void drawEdgeLines(GraphicsContext gc, RoadSegment seg, double sx, double sy, double ex, double ey, double ang, double offS, double offE, double scale) {
        if (scale < 0.3) return;
        double hw = roadWidth(seg, scale) / 2.0 - scale * 0.5;
        double perpX = Math.cos(ang + Math.PI/2), perpY = Math.sin(ang + Math.PI/2);
        gc.setStroke(Color.web("#b0bec8", 0.6));
        gc.setLineWidth(Math.max(0.5, scale * 0.8));
        gc.strokeLine(sx + Math.cos(ang)*offS - perpX*hw, sy + Math.sin(ang)*offS - perpY*hw, ex - Math.cos(ang)*offE - perpX*hw, ey - Math.sin(ang)*offE - perpY*hw);
        gc.strokeLine(sx + Math.cos(ang)*offS + perpX*hw, sy + Math.sin(ang)*offS + perpY*hw, ex - Math.cos(ang)*offE + perpX*hw, ey - Math.sin(ang)*offE + perpY*hw);
    }

    private void drawStopLine(GraphicsContext gc, double px, double py, double roadAngle, RoadSegment seg, double scale) {
        if (scale < 0.4) return;
        double hw = roadWidth(seg, scale) / 2.0;
        double perpX = Math.cos(roadAngle + Math.PI/2), perpY = Math.sin(roadAngle + Math.PI/2);
        double ox = px - Math.cos(roadAngle) * 6 * scale, oy = py - Math.sin(roadAngle) * 6 * scale;
        gc.setStroke(COLOR_MARK_WHITE);
        gc.setLineWidth(Math.max(1.0, 2.2 * scale));
        gc.strokeLine(ox - perpX*hw*0.8, oy - perpY*hw*0.8, ox + perpX*hw*0.8, oy + perpY*hw*0.8);
    }

    private void drawRampCenterLine(GraphicsContext gc, HighwayRampSegment ramp, double scale) {
        double[] b = bezier(ramp);
        gc.setStroke(COLOR_MARK_YELLOW);
        gc.setLineWidth(Math.max(0.8, 1.6 * scale));
        gc.setLineDashes(12*scale, 9*scale);
        gc.beginPath();
        gc.moveTo(b[0], b[1]);
        gc.bezierCurveTo(b[4], b[5], b[6], b[7], b[2], b[3]);
        gc.stroke();
        gc.setLineDashes(null);
    }

    // ════════════════════════════════════════════════════════════
    // LABELS
    // ════════════════════════════════════════════════════════════
    public void drawRoadLabel(GraphicsContext gc, RoadSegment seg) {
        double scale = view.getViewScale();
        double labelX, labelY, angleRad;

        if (seg instanceof HighwayRampSegment ramp) {
            double[] b = bezier(ramp);
            labelX = cubicBezierPoint(0.5, b[0], b[4], b[6], b[2]);
            labelY = cubicBezierPoint(0.5, b[1], b[5], b[7], b[3]);
            angleRad = Math.atan2(cubicBezierTangent(0.5, b[1], b[5], b[7], b[3]), cubicBezierTangent(0.5, b[0], b[4], b[6], b[2]));
        } else {
            labelX = view.toScreenX((seg.getStartX() + seg.getEndX()) / 2.0);
            labelY = view.toScreenY((seg.getStartY() + seg.getEndY()) / 2.0);
            angleRad = seg.getAngle();
        }

        String text = (seg instanceof HighwayRampSegment ramp) ? (ramp.getRampType() == HighwayRampSegment.RampType.ONRAMP ? "ON RAMP" : "OFF RAMP") : (seg instanceof HighwaySegment ? "HWY" : seg.getLanes().size() + " làn");
        drawTextPill(gc, text, labelX, labelY, angleRad, -14 * scale);
    }

    public void drawIntersectionLabel(GraphicsContext gc, IntersectionRenderData data) {
        double tx = view.toScreenX(data.centerX);
        double ty = view.toScreenY(data.centerY);
        String txt = data.typeName + " (" + data.arms.size() + " nhánh)";

        // Vẽ TextPill (Nhãn có nền đen mờ bao quanh giống nhãn của đường)
        drawTextPill(gc, txt, tx, ty, 0, 0);
    }

    public void drawJunctionCore(GraphicsContext gc, double x, double y, double radius) {
        double scale = view.getViewScale();
        double screenX = view.toScreenX(x);
        double screenY = view.toScreenY(y);
        // Chỉ vẽ dấu chấm nhỏ tại tâm nút giao, không đè lên đường
        double dotR = Math.min(radius * scale, 6 * scale);
        if (dotR < 2) return;

        gc.save();
        gc.setFill(Color.web("#5e6060"));
        gc.fillOval(screenX - dotR, screenY - dotR, dotR * 2, dotR * 2);
        gc.restore();
    }

    private void drawTextPill(GraphicsContext gc, String text, double x, double y, double angleRad, double yOffset) {
        double scale = view.getViewScale();
        gc.save();
        gc.translate(x, y);
        double deg = Math.toDegrees(angleRad);
        if (deg > 90 || deg < -90) { gc.rotate(deg + 180); yOffset = -yOffset; } else gc.rotate(deg);
        gc.translate(0, yOffset);
        double fs = Math.max(9, 11 * scale);
        gc.setFont(Font.font("Monospace", FontWeight.BOLD, fs));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.CENTER);
        double pw = text.length() * fs * 0.6 + 10*scale, ph = fs + 6*scale;
        gc.setFill(COLOR_LABEL_BG);
        gc.fillRoundRect(-pw/2, -ph/2, pw, ph, 8*scale, 8*scale);
        gc.setFill(Color.WHITE);
        gc.fillText(text, 0, 0);
        gc.restore();
    }

    // ════════════════════════════════════════════════════════════
    // HOVER HIGHLIGHT (EDIT_MARKINGS mode)
    // ════════════════════════════════════════════════════════════

    /**
     * Vẽ đường highlight màu xanh lam nhạt lên ranh giới đang được hover.
     *
     * @param seg           Đoạn đường chứa ranh giới
     * @param boundaryIndex Chỉ số ranh giới nội bộ (1 .. laneCount-1):
     *                      ranh giới i nằm giữa làn i-1 và làn i
     */
    public void drawBoundaryHighlight(GraphicsContext gc, RoadSegment seg, int boundaryIndex) {
        double scale = view.getViewScale();
        if (scale < 0.18) return;

        List<Lane> lanes = seg.getLanes();
        if (boundaryIndex <= 0 || boundaryIndex >= lanes.size()) return;

        double sx = view.toScreenX(seg.getStartX()), sy = view.toScreenY(seg.getStartY());
        double ex = view.toScreenX(seg.getEndX()),   ey = view.toScreenY(seg.getEndY());
        double ang = Math.atan2(ey - sy, ex - sx);
        double perpX = Math.cos(ang + Math.PI / 2), perpY = Math.sin(ang + Math.PI / 2);

        // ĐỒNG BỘ: Tính toán vị trí vạch dùng hệ số 20.0
        double totalWorldW = lanes.stream().mapToDouble(l -> (l.getWidth() / 3.5) * 20.0).sum();
        double offsetWorld = -totalWorldW / 2.0;
        for (int i = 0; i < boundaryIndex; i++) {
            offsetWorld += (lanes.get(i).getWidth() / 3.5) * 20.0;
        }
        double offsetPx = offsetWorld * scale;

        double lx1 = sx + perpX * offsetPx, ly1 = sy + perpY * offsetPx;
        double lx2 = ex + perpX * offsetPx, ly2 = ey + perpY * offsetPx;

        gc.save();
        gc.setStroke(Color.web("#40aaff", 0.5)); // Tăng độ rực lên 0.5 cho dễ nhìn
        gc.setLineWidth(Math.max(6, 12 * scale)); // Vạch xanh hover bự ra
        gc.setLineDashes(null);
        gc.strokeLine(lx1, ly1, lx2, ly2);
        gc.restore();
    }

    // ════════════════════════════════════════════════════════════
    // HELPERS
    // ════════════════════════════════════════════════════════════
    public double roadWidth(RoadSegment seg, double scale) {
        return Math.max(seg.getLaneCount(), 2) * LANE_PX * scale;
    }

    public double[] bezier(HighwayRampSegment ramp) {
        double sx = view.toScreenX(ramp.getStartX()), sy = view.toScreenY(ramp.getStartY());
        double ex = view.toScreenX(ramp.getEndX()),   ey = view.toScreenY(ramp.getEndY());
        double pull = Math.hypot(ex-sx, ey-sy) * 0.44;
        HighwaySegment hwy = ramp.getConnectedHighway();
        double hdx = Math.cos(hwy != null ? hwy.getAngle() : ramp.getAngle()), hdy = Math.sin(hwy != null ? hwy.getAngle() : ramp.getAngle());
        return new double[]{sx, sy, ex, ey, sx + Math.cos(ramp.getAngle())*pull, sy + Math.sin(ramp.getAngle())*pull, ex - hdx*pull, ey - hdy*pull};
    }

    private double cubicBezierPoint(double t, double p0, double p1, double p2, double p3) {
        double u = 1-t; return u*u*u*p0 + 3*u*u*t*p1 + 3*u*t*t*p2 + t*t*t*p3;
    }

    private double cubicBezierTangent(double t, double p0, double p1, double p2, double p3) {
        double u = 1-t; return 3*(u*u*(p1-p0) + 2*u*t*(p2-p1) + t*t*(p3-p2));
    }

    private Lane.MarkingType getCenterMarkingType(RoadSegment seg) {
        List<Lane> lanes = seg.getLanes();
        for (Lane l : lanes) if (l.getDirection() == Lane.Direction.FORWARD) return l.getRightMarking();
        return Lane.MarkingType.DASHED;
    }

    public boolean samePoint(double x1, double y1, double x2, double y2) {
        return Math.abs(x1-x2) < 0.5 && Math.abs(y1-y2) < 0.5;
    }
}