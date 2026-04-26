Project Structure

traffic-simulation/
 ├── src/
 │    ├── main/
 │    │    ├── java/
 │    │    │    └── com/myteam/traffic/
 │    │    │         ├── behavior/      # Hành vi driver (Normal, Aggressive, ...)
 │    │    │         ├── vehicle/       # Các phương tiện (Car, Bike, ...)
 │    │    │         ├── road/          # Các loại đường (Intersection - nút giao, Highway - đường cao tốc, ...)
 │    │    │         ├── rule/          # Luật giao thông (TrafficRule, ...)
 │    │    │         ├── sign/          # Biển báo giao thông
 │    │    │         ├── light/         # Đèn giao thông
 │    │    │         ├── marking/       # Vạch kẻ đường
 │    │    │         ├── controller/    # Điều khiển hệ thống giao thông
 │    │    │         └── ui/            # Giao diện người dùng
 │    │    └── resources/               # Hình ảnh, âm thanh (nếu có)
 │    └── test/                         # Code test (nếu cần)
 │
 ├── docs/                              # UML, slide, tài liệu
 ├── .gitignore                         # Bắt buộc (tránh commit file rác)
 ├── README.md                          # Mô tả project
 └── pom.xml                            # Nếu dùng Maven

Guidelines

- Mỗi người làm việc trong package của mình để tránh conflict
- Không sửa code của người khác nếu chưa trao đổi
- Luôn pull code mới nhất trước khi push
- Không commit các file build (bin/, *.class)
- Tuân thủ cấu trúc package như trên

Responsibilities

- behavior, vehicle, road: core simulation
- rule, sign, light, marking: traffic logic
- controller: điều phối hệ thống
- ui: hiển thị