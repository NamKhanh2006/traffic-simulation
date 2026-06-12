package com.myteam.traffic.ui;

import com.myteam.traffic.vehicle.Vehicle; 
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;

public class VehicleRenderer {
    
    // Khai báo biến lưu trữ hình ảnh
    private Image carImage;
    private Image ambuImage;
    private Image fireTruckImage;
    private Image motorbikeImage;
    private Image bicycleImage;

    public VehicleRenderer() {
        // Tải ảnh vào bộ nhớ khi khởi tạo Renderer
        try {
            carImage = new Image(getClass().getResourceAsStream("/images/car.png"));
            ambuImage = new Image(getClass().getResourceAsStream("/images/ambulance.png"));
            fireTruckImage = new Image(getClass().getResourceAsStream("/images/firetruck.png"));
            motorbikeImage = new Image(getClass().getResourceAsStream("/images/motorbike.png"));
            bicycleImage = new Image(getClass().getResourceAsStream("/images/bicycle.png"));
        } catch (Exception e) {
            System.out.println("Lỗi tải ảnh: " + e.getMessage());
        }
    }

    // Hàm này sẽ được gọi liên tục mỗi khung hình (Frame)
    public void render(GraphicsContext gc, Vehicle vehicle, boolean isGraphicMode) {
        // Lấy tọa độ từ logic
        double x = vehicle.getX();
        double y = vehicle.getY();
        double angle = vehicle.getDirection().toDegrees(); // Lấy hướng (góc) của xe
        
        // Kích thước vẽ mặc định. Có thể thay bằng vehicle.getWidth() * tỉ lệ (scale)
        double width = 40; 
        double height = 20;

        // Lưu trạng thái của GraphicsContext trước khi bắt đầu biến đổi
        gc.save();
        
        // Dịch chuyển trục tọa độ đến tâm của phương tiện
        gc.translate(x + width / 2, y + height / 2);
        
        // Xoay theo góc của phương tiện (Vehicle sử dụng radian nên cần đổi sang độ)
        gc.rotate(Math.toDegrees(angle));
        
        if (!isGraphicMode) {
            // 1. Chế độ BASIC: Chỉ vẽ hình chữ nhật đơn giản
            gc.setFill(Color.BLUE);
            gc.fillRect(-width / 2, -height / 2, width, height); // Vẽ với tâm là 0, 0 mới
            gc.setFill(Color.WHITE);
            gc.fillText(vehicle.getType().toString(), -width / 2 + 5, -height / 2 + 15); // Ghi loại xe lên khối
        } else {
            // 2. Chế độ ĐỒ HỌA: Vẽ hình ảnh Image
            Image imageToDraw = carImage; // Mặc định là ô tô
            
            String type = vehicle.getType() != null ? vehicle.getType().toString() : "";
            switch (type) {
                case "Ambulance":
                    imageToDraw = ambuImage;
                    break;
                case "FireTruck":
                    imageToDraw = fireTruckImage;
                    break;
                case "Motorbike":
                    imageToDraw = motorbikeImage;
                    break;
                case "Bicycle":
                    imageToDraw = bicycleImage;
                    break;
                default:
                    if (vehicle.isEmergency()) { // Backup cho các loại xe cứu hộ khác
                        imageToDraw = ambuImage;
                    }
                    break;
            }
            
            // Fallback an toàn nếu có ảnh bị lỗi khi load
            if (imageToDraw == null) imageToDraw = carImage;

            // Lệnh vẽ ảnh: (hình_ảnh, tọa_độ_x, tọa_độ_y, chiều_rộng, chiều_cao)
            gc.drawImage(imageToDraw, -width / 2, -height / 2, width, height);
        }
        
        // Phục hồi lại trạng thái GraphicsContext như ban đầu (để tránh ảnh hưởng tới xe vẽ sau)
        gc.restore();
    }
}