# Cà Phê Hạt Nâu — Quản lý quán cà phê

Ứng dụng desktop quản lý quán cà phê được xây dựng bằng Java Swing, FlatLaf, JDBC và Maven. Hệ thống hỗ trợ quy trình bán hàng tại bàn hoặc mang đi, quản lý hóa đơn, khách hàng, thực đơn, kho nguyên liệu, ca thu ngân, báo cáo và phân quyền người dùng.

Dự án sử dụng cách tổ chức mã nguồn theo các lớp `model`, `dao`, `service`, `controller` và `view`, giúp tách biệt dữ liệu, truy cập cơ sở dữ liệu, quy tắc nghiệp vụ và giao diện người dùng.

## Công nghệ

- Java: sử dụng phiên bản phù hợp với cấu hình compiler trong `pom.xml`.
- Java Swing và FlatLaf: xây dựng giao diện desktop.
- JDBC: truy cập cơ sở dữ liệu.
- Maven: quản lý dependencies, build và kiểm thử.
- H2: sử dụng cho các bài kiểm thử độc lập và môi trường thử nghiệm phù hợp.
- JUnit 5: kiểm thử tự động theo cấu hình dự án.
- jBCrypt: hỗ trợ xử lý mật khẩu theo cấu hình hiện có của dự án.

### Trạng thái hỗ trợ cơ sở dữ liệu

- **H2:** được sử dụng trong các bài kiểm thử độc lập. Nên dùng cơ sở dữ liệu riêng cho demo và UAT.
- **MySQL:** tài liệu phiên bản trước có đề cập driver và cấu hình MySQL. Việc khởi tạo, migration và vận hành trên máy chủ MySQL thực tế cần được xác minh riêng.
- **Microsoft SQL Server:** chưa được xác nhận là backend được hỗ trợ hoàn chỉnh. Việc cài đặt SQL Server Database Engine không tự động làm ứng dụng tương thích. Chỉ sử dụng sau khi driver, cấu hình, schema, migration và kiểm thử tích hợp đã được xác minh.

Không sử dụng thông tin trong phần công nghệ thay cho kết quả kiểm thử thực tế.

## Chức năng chính

- Đăng nhập, thiết lập tài khoản OWNER đầu tiên và phân quyền OWNER, MANAGER, EMPLOYEE.
- POS: quản lý bàn, khu vực, món, size, topping, đơn hàng và thanh toán.
- Quản lý hóa đơn, khách hàng và đặt bàn.
- Quản lý thực đơn, bảng giá, khuyến mãi và thuế.
- Quản lý kho nguyên liệu, công thức, nhà cung cấp và phiếu nhập/xuất.
- Quản lý ca thu ngân, tiền đầu ca/cuối ca, khoản thu/chi, điều chỉnh tiền và chênh lệch.
- Báo cáo doanh thu và nhật ký kiểm toán theo quyền truy cập.

Phạm vi dữ liệu người dùng được xem hoặc thay đổi phải tuân theo quy tắc phân quyền được triển khai trong ứng dụng.

## Cấu trúc dự án

```text
Coffee-Hat-Nau/
├── src/
│   ├── main/
│   │   ├── java/cafe/
│   │   │   ├── config/       # Cấu hình và kết nối JDBC
│   │   │   ├── controller/   # Điều phối luồng ứng dụng
│   │   │   ├── dao/          # Truy vấn và truy cập dữ liệu
│   │   │   ├── model/        # Model, enum và dữ liệu nghiệp vụ
│   │   │   ├── service/      # Quy tắc nghiệp vụ và phân quyền
│   │   │   ├── util/         # Tiện ích dùng chung
│   │   │   └── view/         # Giao diện Java Swing
│   │   └── resources/db/
│   │       ├── schema.sql
│   │       ├── migration-v1.sql
│   │       ├── migration-v2.sql
│   │       └── seed-demo.sql
│   └── test/java/cafe/       # Unit, integration và UI smoke tests
├── docs/
├── pom.xml
├── config.example.properties
├── AGENTS.md
└── README.md
```

Cấu trúc trên mô tả những thành phần chính; hãy đối chiếu source thực tế khi cần biết đầy đủ danh sách lớp và tài nguyên.

## Yêu cầu môi trường

- JDK tương thích với phiên bản được khai báo trong `pom.xml`.
- Maven tương thích với cấu hình dự án.
- IntelliJ IDEA hoặc IDE Java tương đương.

## Mở dự án bằng IntelliJ IDEA

1. Clone repository.
2. Mở thư mục dự án bằng IntelliJ IDEA.
3. Chọn JDK phù hợp với `pom.xml`.
4. Reload Maven và chờ tải dependencies.
5. Tìm lớp có phương thức `main()` trong source; lớp khởi chạy dự kiến là `cafe.App`.
6. Chạy ứng dụng theo cấu hình phù hợp với môi trường và cơ sở dữ liệu đã thiết lập.

## Build và kiểm thử

Từ thư mục gốc của dự án:

```bash
mvn clean verify
```

Đóng gói ứng dụng:

```bash
mvn clean package
```

Tên file JAR và vị trí artifact được xác định bởi cấu hình trong `pom.xml`. Kiểm tra thư mục `target/` sau khi build; không giả định rằng JAR đã được đóng gói kèm tất cả dependencies nếu chưa xác minh cấu hình Maven.

Các bài kiểm thử tự động nên sử dụng cơ sở dữ liệu thử nghiệm riêng. Kết quả test với H2 không đồng nghĩa với việc tích hợp trên máy chủ MySQL hoặc SQL Server đã được kiểm chứng.

## Cấu hình cơ sở dữ liệu

1. Xem `config.example.properties` để xác định các thuộc tính cấu hình được hỗ trợ.
2. Tạo file cấu hình cục bộ theo hướng dẫn trong source và tài liệu dự án.
3. Điền thông tin kết nối của database thử nghiệm riêng.
4. Xác minh schema và cơ chế migration trước khi khởi chạy.
5. Không chạy migration thử nghiệm trên cơ sở dữ liệu đang vận hành.

Database mới có thể có quy trình thiết lập tài khoản OWNER đầu tiên tùy theo luồng khởi tạo được triển khai. Không giả định ứng dụng có mật khẩu mặc định; hãy thực hiện theo giao diện thiết lập ban đầu.

Các file SQL trong `src/main/resources/db/` là tài nguyên của dự án. Không chạy script schema, migration hoặc seed demo trên database thật khi chưa xác minh tác động.

## An toàn dữ liệu

Không commit các loại dữ liệu sau:

- `config.properties` hoặc file cấu hình chứa thông tin xác thực thật.
- Mật khẩu, API key, chuỗi kết nối nhạy cảm hoặc thông tin tài khoản.
- Database cục bộ, dữ liệu vận hành, file backup và bản dump.
- Thư mục `target/`, log, ảnh chụp màn hình và kết quả render giao diện.
- File cấu hình IDE cá nhân hoặc cấu hình Maven riêng tư.

Kiểm tra `.gitignore` và danh sách file đã stage trước mỗi commit. Chỉ đưa lên Git các schema, migration, mã nguồn, dữ liệu demo đã được xác nhận an toàn và tài liệu cần thiết.

## Tài liệu dự án

- [Hướng dẫn dự án](docs/PROJECT_GUIDE.md)
- [Bản đồ mã nguồn](docs/CODE_MAP.md)
- [Sơ đồ lớp](docs/CLASS_DIAGRAM.md)
- [Các vấn đề đã biết](docs/KNOWN_ISSUES.md)
- [Checklist nghiệm thu](docs/ACCEPTANCE_CHECKLIST.md)

