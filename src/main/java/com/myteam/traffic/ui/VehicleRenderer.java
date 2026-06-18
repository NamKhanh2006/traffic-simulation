package com.myteam.traffic.ui;

import com.myteam.traffic.vehicle.Vehicle;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

public class VehicleRenderer {

    // Kích thước (L = Chiều dài trục X, W = Chiều rộng trục Y)
    public static final double CAR_L   = 20.0;
    public static final double CAR_W   = 10.0;
    public static final double MOTO_L  = 12.0;
    public static final double MOTO_W  = 5.0;
    public static final double BIKE_L  = 10.0;
    public static final double BIKE_W  = 4.0;
    public static final double TRUCK_L = 28.0;
    public static final double TRUCK_W = 12.0;

    private com.myteam.traffic.controller.VehicleSpawner spawner;

    public VehicleRenderer() {}

    public void setSpawner(com.myteam.traffic.controller.VehicleSpawner spawner) {
        this.spawner = spawner;
    }

    public void render(GraphicsContext gc, Vehicle vehicle, boolean isGraphicMode) {
        double x = vehicle.getX();
        double y = vehicle.getY();
        double angleDeg = vehicle.getDirection().toDegrees();

        double[] dims = getDimensions(vehicle);
        double l = dims[0]; // Chiều dài
        double w = dims[1]; // Chiều rộng

        double spawnAlpha = (spawner != null) ? spawner.getSpawnAlpha(vehicle) : 1.0;
        double dyingAlpha = vehicle.getDyingAlpha();
        double alpha = spawnAlpha * dyingAlpha;

        gc.save();
        gc.setGlobalAlpha(alpha);

        // Dịch chuyển về tâm xe và xoay theo hướng di chuyển
        gc.translate(x, y);
        gc.rotate(angleDeg);

        if (vehicle.getType() == com.myteam.traffic.vehicle.VehicleType.BICYCLE || 
            vehicle.getType() == com.myteam.traffic.vehicle.VehicleType.MOTORBIKE) {
            
            boolean isMoto = (vehicle.getType() == com.myteam.traffic.vehicle.VehicleType.MOTORBIKE);
            double wheelR = isMoto ? 1.8 : 1.2;
            
            // 1. Bánh xe (đen sẫm)
            gc.setFill(Color.web("#2c3e50")); 
            gc.fillOval(l/2 - wheelR*2, -wheelR, wheelR*2, wheelR*2); // Bánh trước
            gc.fillOval(-l/2, -wheelR, wheelR*2, wheelR*2); // Bánh sau

            // 2. Thân/Khung xe
            gc.setStroke(getBaseColor(vehicle));
            gc.setLineWidth(isMoto ? 3.5 : 1.5);
            gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
            gc.strokeLine(-l/2 + wheelR, 0, l/2 - wheelR, 0);

            // 3. Tay lái
            gc.setStroke(Color.web("#bdc3c7"));
            gc.setLineWidth(isMoto ? 1.5 : 1.0);
            gc.strokeLine(l/4, -w/2 + 0.5, l/4, w/2 - 0.5);

            // 4. Người lái (đội mũ bảo hiểm / đầu)
            gc.setFill(Color.web("#34495e"));
            double riderR = isMoto ? 2.2 : 1.8;
            gc.fillOval(-riderR/2, -riderR, riderR*2, riderR*2); // Vị trí người lái lùi về sau tay lái

            // 5. Đèn xe cho xe máy
            if (isMoto) {
                // Đèn pha trước
                gc.setFill(Color.web("#f1c40f", 0.9));
                gc.fillOval(l/2 - 2.0, -1.0, 2.5, 2.0);
                
                // Đèn hậu đỏ sau
                gc.setFill(Color.web("#ff0000", 0.9));
                gc.fillOval(-l/2 - 1.0, -1.0, 1.5, 2.0);
            }

        } else {
            // Logic vẽ ô tô hiện tại
            // 1. Vẽ thân xe dạng hình chữ nhật bo góc
            Color baseColor = getBaseColor(vehicle);
            gc.setFill(baseColor);
            gc.fillRoundRect(-l / 2, -w / 2, l, w, 4, 4);

            // Vẽ viền xe
            gc.setStroke(baseColor.darker());
            gc.setLineWidth(1.0);
            gc.strokeRoundRect(-l / 2, -w / 2, l, w, 4, 4);

            // 2. Vẽ kính chắn gió phía ĐẦU xe (trục X dương)
            gc.setFill(Color.web("#8cd4ff", 0.8)); // Kính màu xanh lơ
            gc.fillRect(l / 4 - 1, -w / 2 + 1.5, l * 0.15, w - 3);

            // 3. Kính hậu phía ĐUÔI xe
            gc.setFill(Color.web("#2c3e50", 0.8)); // Kính tối màu
            gc.fillRect(-l / 2 + 2, -w / 2 + 2, l * 0.1, w - 4);

            // 4. ĐÈN PHA (Đầu xe - màu vàng sáng)
            gc.setFill(Color.web("#f1c40f", 0.9));
            gc.fillOval(l / 2 - 2, -w / 2 + 1, 2, 2.5); // Pha trái
            gc.fillOval(l / 2 - 2, w / 2 - 3.5, 2, 2.5); // Pha phải

            // 5. ĐÈN HẬU (Đuôi xe - màu đỏ rực)
            gc.setFill(Color.web("#ff0000", 0.9));
            gc.fillOval(-l / 2, -w / 2 + 1, 2, 2.5); // Hậu trái
            gc.fillOval(-l / 2, w / 2 - 3.5, 2, 2.5); // Hậu phải

            // Nếu là xe ưu tiên (cứu hỏa/cứu thương), vẽ đèn còi trên nóc
            if (vehicle.isEmergency()) {
                boolean sirenActive = true;
                if (vehicle instanceof com.myteam.traffic.vehicle.emergency.EmergencyVehicle) {
                    sirenActive = ((com.myteam.traffic.vehicle.emergency.EmergencyVehicle) vehicle).isSirenOn();
                }

                // Đèn cơ bản
                gc.setFill(Color.BLUE.darker());
                gc.fillRect(-2, -w / 2 + 1, 4, 3);
                gc.setFill(Color.RED.darker());
                gc.fillRect(-2, w / 2 - 4, 4, 3);

                // Hiệu ứng chớp sáng khi bật còi
                if (sirenActive) {
                    long time = System.currentTimeMillis();
                    if ((time / 150) % 2 == 0) {
                        gc.setFill(Color.RED);
                        gc.fillRect(-3, -w / 2 + 0.5, 6, 4); // Chớp đỏ to hơn
                    } else {
                        gc.setFill(Color.BLUE);
                        gc.fillRect(-3, w / 2 - 4.5, 6, 4);  // Chớp xanh to hơn
                    }
                }
            }
        }

        gc.restore();
    }

    private double[] getDimensions(Vehicle v) {
        if (v.getType() == null) return new double[]{CAR_L, CAR_W};
        return switch (v.getType()) {
            case MOTORBIKE         -> new double[]{MOTO_L,  MOTO_W};
            case BICYCLE           -> new double[]{BIKE_L,  BIKE_W};
            case AMBULANCE,
                 FIRETRUCK         -> new double[]{TRUCK_L, TRUCK_W};
            default                -> new double[]{CAR_L,   CAR_W};
        };
    }

    private Color getBaseColor(Vehicle v) {
        if (v.getType() == null) return Color.web("#e74c3c");
        return switch (v.getType()) {
            case CAR        -> Color.web("#e74c3c"); // Đỏ
            case MOTORBIKE  -> Color.web("#f39c12"); // Vàng cam
            case BICYCLE    -> Color.web("#2ecc71"); // Xanh lá
            case AMBULANCE  -> Color.web("#ecf0f1"); // Trắng
            case FIRETRUCK  -> Color.web("#c0392b"); // Đỏ sẫm
            default         -> Color.web("#3498db");
        };
    }
}