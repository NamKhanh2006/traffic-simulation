# Mô Phỏng Giao Thông (Traffic Simulation)

Đây là một dự án phần mềm mô phỏng giao thông cơ bản được xây dựng bằng ngôn ngữ Java và giao diện JavaFX. Dự án tập trung vào việc mô phỏng chuyển động của các phương tiện, hành vi của người lái và sự tuân thủ các quy tắc giao thông cơ bản tại các nút giao.

![Giao diện mô phỏng](docs/images/Screenshot%202026-07-21%20190423.png)

## Tính Năng Chính

*   **Đa dạng phương tiện:** Hỗ trợ mô phỏng các loại phương tiện như Ô tô, Xe máy, Xe đạp, cùng với các phương tiện ưu tiên (khẩn cấp).
*   **Hành vi lái xe:** Tích hợp các kiểu hành vi lái xe khác nhau như Bình thường (Normal), Nóng nảy (Aggressive), và Khẩn cấp (Emergency) ảnh hưởng đến tốc độ và cách xử lý tình huống.
*   **Hệ thống đường và nút giao:** Mô phỏng sự di chuyển trên các đoạn đường và cách xử lý khi qua các ngã tư có đèn tín hiệu.
*   **Tuân thủ luật giao thông:** Các phương tiện được lập trình để biết dừng đèn đỏ, giữ khoảng cách an toàn với xe phía trước và nhận diện vạch kẻ đường.
*   **Tùy chỉnh mô phỏng:** Hệ thống điều phối (Controller) cho phép quản lý luồng giao thông và mật độ sinh xe (Vehicle Spawner).
*   **Giao diện trực quan (UI):** Giao diện 2D xây dựng bằng JavaFX, cho phép chuyển đổi chế độ hiển thị linh hoạt (ví dụ: hiển thị khối hình cơ bản hoặc hiển thị theo hình ảnh phương tiện).

## Cấu Trúc Mã Nguồn

*   `behavior/`: Logic về hành vi của các tài xế.
*   `vehicle/`: Các lớp đối tượng định nghĩa phương tiện.
*   `rule/`: Các quy tắc giao thông (khoảng cách an toàn, đèn tín hiệu, vạch kẻ đường, v.v.).
*   `controller/`: Lớp điều phối hệ thống giao thông tổng thể và quản lý sinh phương tiện.
*   `ui/`: Xử lý giao diện đồ họa, bao gồm các lớp Renderer và ứng dụng JavaFX.
*   `model/`: Chứa các cấu trúc hạ tầng đường sá và chính sách giao thông.

## Hướng Dẫn Cài Đặt

### Yêu cầu
*   Java Development Kit (JDK) 25 trở lên.
*   Apache Maven để quản lý thư viện và chạy dự án.

### Cách chạy dự án
1.  Clone mã nguồn dự án về máy.
2.  Mở terminal tại thư mục gốc của dự án và chạy lệnh sau:
    ```bash
    mvn javafx:run
    ```