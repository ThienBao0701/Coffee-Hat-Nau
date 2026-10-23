# Cà Phê Hạt Nâu — Quản lý quán cà phê

Ứng dụng desktop quản lý quán cà phê được xây dựng bằng Java Swing, FlatLaf và JDBC. Dự án tổ chức theo các lớp nghiệp vụ quen thuộc trong bài tập Java Swing/JDBC: model, dao, service, controller và view.

## Công nghệ

- Java (xem `pom.xml` để biết phiên bản compile target)
- Java Swing + FlatLaf
- JDBC
- H2 cho kiểm thử/UAT độc lập
- Microsoft SQL Server đang được tích hợp theo cấu hình riêng
- Maven

## Cấu trúc dự án

```text
Coffee-Hat-Nau/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── cafe/
│   │   │       ├── model/         # Các lớp dữ liệu/nghiệp vụ
│   │   │       ├── dao/           # Truy vấn JDBC
│   │   │       ├── service/       # Quy tắc nghiệp vụ và phân quyền
│   │   │       ├── controller/   # Điều phối thao tác giao diện
│   │   │       ├── view/          # Java Swing UI
│   │   │       └── ...            # Config và tiện ích theo source thực tế
│   │   └── resources/
│   │       └── db/                # Schema, migration, dữ liệu mẫu
│   └── test/
│       └── java/                  # Unit/integration/UI smoke tests
├── docs/                          # Hướng dẫn, sơ đồ lớp, bản đồ code
├── pom.xml
├── config.example.properties
├── config.sqlserver.example.properties
└── README.md
```

> Sơ đồ trên là định hướng tổ chức dễ học, không yêu cầu đổi package hiện tại một cách máy móc. Khi đưa source vào repository, hãy giữ đúng các package và đường dẫn thật của dự án.

## Các chức năng chính

- Đăng nhập, thiết lập tài khoản chủ cửa hàng và phân quyền OWNER/MANAGER/EMPLOYEE.
- POS: bàn/khu vực, thực đơn, size, topping, đơn hàng và thanh toán.
- Hóa đơn, khách hàng và đặt bàn.
- Thực đơn, bảng giá, khuyến mãi và thuế.
- Kho nguyên liệu, công thức, nhà cung cấp, phiếu nhập/xuất và lịch sử tồn.
- Ca thu ngân, tiền đầu ca/cuối ca, ghi thu/chi/rút tiền và đối soát chênh lệch.
- Báo cáo doanh thu và nhật ký kiểm toán.

## Mở bằng IntelliJ IDEA

1. Clone repository.
2. Mở thư mục dự án bằng IntelliJ IDEA.
3. Chọn JDK phù hợp với cấu hình trong `pom.xml`.
4. Reload Maven và chờ tải dependencies.
5. Chạy class có `main()` (kiểm tra trong source, thường là `cafe.App`).

## Build và test

```bash
mvn clean verify
```

Đóng gói JAR:

```bash
mvn clean package
```

Tên và đường dẫn JAR được xác định theo cấu hình `pom.xml`.

## Cấu hình cơ sở dữ liệu

- Dùng file cấu hình cục bộ được chỉ định bởi ứng dụng; xem `config.example.properties`.
- Không commit `config.properties`, mật khẩu, file database H2, dữ liệu thật hoặc file backup.
- Dùng H2/in-memory cho kiểm thử độc lập.
- Chỉ dùng SQL Server khi SQL Server Database Engine đã được cài và cấu hình. Xem `config.sqlserver.example.properties` (nếu có) và hướng dẫn database trong `docs/`.
- Kiểm thử trên database riêng như `CafeHatNau_UAT`; không chạy migration thử nghiệm vào database đang vận hành.

## An toàn dữ liệu

Mặc định không đưa lên Git:
- `config.properties` và mọi file chứa thông tin xác thực thật.
- H2 database (`*.mv.db`, `*.trace.db`), backup và dữ liệu vận hành.
- Thư mục `target/`, log, ảnh screenshot, và file IDE cá nhân.

Hãy kiểm tra `.gitignore` trước mỗi lần commit.

## Tài liệu dự án

Xem thư mục `docs/` để đọc hướng dẫn dự án, sơ đồ class, bản đồ code và các giới hạn đã biết.
