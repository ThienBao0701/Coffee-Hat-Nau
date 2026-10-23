# Cà Phê Hạt Nâu — Quản lý quán cà phê

Ứng dụng desktop Java Swing cho quán cà phê: bán hàng tại bàn hoặc mang đi, quản lý hóa đơn, khách hàng, kho nguyên liệu, ca thu ngân, báo cáo và phân quyền OWNER/MANAGER/EMPLOYEE. Giao diện tiếng Việt, dùng FlatLaf.

## Công nghệ

- Java 17, Swing, FlatLaf 3.7.2 và Maven.
- JDBC với H2 nhúng (mặc định, dùng cho demo/kiểm thử) hoặc MySQL 8 (cần máy chủ riêng).
- jBCrypt để băm mật khẩu; JUnit 5 cho kiểm thử.

MySQL có driver và cấu hình trong dự án, nhưng kiểm thử tự động dùng H2 ở chế độ tương thích MySQL; việc khởi tạo và migration trên máy chủ MySQL thật chưa được xác minh. Dự án hiện không có driver hay cấu hình SQL Server.

## Mở và chạy

Cần JDK 17 trở lên và Maven 3.8 trở lên. Trong IntelliJ IDEA hoặc Eclipse, mở thư mục này như một dự án Maven, nạp lại dependencies rồi chạy `cafe.App` với working directory là thư mục dự án.

```bash
mvn test
mvn package
java -jar target/cafe-management.jar
```

JAR đã đóng gói dependencies và có `cafe.App` làm main class. Nếu không có `config.properties`, ứng dụng dùng H2 tại `./data/cafe`. Để tùy chỉnh, sao chép `config.example.properties` thành `config.properties`, sửa cấu hình rồi chạy ứng dụng từ thư mục chứa file đó. Có thể chỉ định đường dẫn khác bằng `-Dcafe.config=đường-dẫn/config.properties`.

### MySQL

Tạo trước database trống và tài khoản ứng dụng trên máy chủ MySQL 8. Sao chép ví dụ cấu hình rồi thay `db.url`, `db.user` và `db.password` bằng thông tin của database thử nghiệm riêng. Không dùng database đang vận hành để chạy thử hoặc migration. Cấu hình H2 trong file ví dụ đang bật; cần thay bằng các dòng MySQL trước khi chạy trên MySQL.

### Tài khoản OWNER đầu tiên và schema

Database mới sẽ hiện màn hình thiết lập để tạo OWNER đầu tiên. Không có tài khoản hoặc mật khẩu mặc định. `db.autoInit=true` cho phép tạo schema khi database còn trống và áp dụng các migration bổ sung có phiên bản. Ứng dụng không dùng cơ chế này để xóa hay tạo lại database hiện hữu. Sao lưu dữ liệu trước khi nâng cấp và chỉ diễn tập trên bản sao riêng.

## Cấu trúc mã nguồn

```text
src/main/java/cafe/
├── App.java                 # Điểm vào ứng dụng
├── config/                  # Cấu hình và kết nối/giao dịch JDBC
├── controller/              # Luồng khởi động, đăng nhập và đăng xuất
├── dao/                     # SQL và ánh xạ dữ liệu JDBC
├── model/                   # Entity, record, enum và dữ liệu báo cáo
├── service/                 # Nghiệp vụ, phân quyền và giao dịch
├── util/                    # Session, quyền, tiền, ngày và kiểm tra dữ liệu
└── view/                    # Swing UI; nhóm admin, catalog, common, inventory, sales

src/main/resources/db/       # Schema, migration và dữ liệu demo
src/test/java/cafe/          # Kiểm thử và công cụ smoke/render UI
```

View gọi service; service thực thi quy tắc và gọi DAO. Tài liệu chi tiết:

- [Hướng dẫn dự án](docs/PROJECT_GUIDE.md)
- [Bản đồ chức năng và mã nguồn](docs/CODE_MAP.md)
- [Sơ đồ lớp](docs/CLASS_DIAGRAM.md)
- [Giới hạn đã biết](docs/KNOWN_ISSUES.md)
- [Checklist demo và kiểm thử thủ công](docs/ACCEPTANCE_CHECKLIST.md)

## Kiểm thử an toàn

`mvn test` dùng H2 riêng cho từng test; kiểm thử lưu trữ dùng thư mục tạm. Không trỏ kiểm thử vào `./data/cafe` hoặc database của cửa hàng. Chỉ kiểm tra MySQL trên database xác minh riêng; H2 không xác nhận tương thích hoàn toàn với máy chủ MySQL.

Trước khi commit, kiểm tra `.gitignore`. Không đưa lên Git `config.properties`, thông tin đăng nhập thật, file database/backup, `data/`, `logs/`, `target/`, ảnh kết quả render, file cấu hình Maven riêng tư hoặc cài đặt IDE cá nhân. Giữ lại các file SQL trong `src/main/resources/db/` vì đó là schema, migration và seed data của dự án.