# Demo và kiểm thử thủ công

Checklist này bổ sung cho kiểm thử tự động; dùng database H2 riêng, không dùng dữ liệu của cửa hàng. Việc tạo schema, đăng nhập và một số luồng vai trò đã có kiểm thử tự động, nhưng thao tác toàn bộ quy trình Swing vẫn cần được kiểm tra trên máy đích.

## Demo chức năng (khoảng 15 phút)

1. Khởi động với database trống và tạo OWNER trên màn hình **Thiết lập lần đầu**.
2. Đăng nhập OWNER; tạo nhân viên có mật khẩu tạm và, nếu cần, tạo tài khoản MANAGER.
3. Mở **Thực đơn & giá** để xem món, size, topping và bảng giá; thử tạo bảng giá tương lai từ bảng hiện tại.
4. Đăng xuất, đăng nhập tài khoản nhân viên; đổi mật khẩu tạm và mở ca với tiền đầu ca. So sánh menu theo vai trò.
5. Trong **Bán hàng**, mở Bàn 03, thêm Cà phê sữa size M ×2 với topping espresso và ghi chú; thêm Bạc xỉu size L; thử sửa số lượng và chuyển bàn.
6. Tạo đơn mang đi rồi gộp vào đơn tại bàn.
7. Thanh toán bằng tiền mặt với khuyến mãi và khách hàng; xem tiền thừa, biên nhận và bản xem trước in. Kiểm tra giao dịch tiền mặt xuất hiện đúng một lần trong ca.
8. Mở **Hóa đơn** với nhân viên và xác nhận chỉ xem được hóa đơn thuộc phạm vi của mình; thử quyền quản lý phải bị service từ chối.
9. Với MANAGER, xem lịch sử kho, tạo rồi hủy phiếu nhập để thấy bút toán đảo; xem cảnh báo tồn thấp.
10. Thử thanh toán đơn có số lượng vượt tồn; giao dịch phải bị từ chối mà không ghi dở dang.
11. Ghi thu/chi/rút tiền, đếm quỹ và đóng ca có giải thích chênh lệch. Xem ca đã đóng; thêm điều chỉnh có lý do và xác nhận số liệu đóng ca gốc vẫn được giữ. Nếu còn đơn mở, thử bàn giao có xác nhận quản lý.
12. Hoàn tiền hóa đơn, sau đó xem báo cáo và nhật ký: doanh thu, giảm giá, hoàn tiền, phương thức thanh toán, người tạo hóa đơn/thu ngân, tồn kho và lịch sử điều chỉnh ca.
13. Tạo đặt bàn, thử đặt trùng, xác nhận và nhận bàn; trạng thái bàn cần cập nhật đúng.

## Kiểm tra thủ công trước triển khai

Tạo file cấu hình riêng, ví dụ `C:\Cafe-UAT\config.properties`:

```properties
db.url=jdbc:h2:C:/Cafe-UAT/cafe;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE
db.user=sa
db.password=
db.autoInit=true
db.seedDemoData=true
```

Chạy JAR từ PowerShell với cấu hình tách biệt:

```powershell
java "-Dcafe.config=C:\Cafe-UAT\config.properties" -jar "C:\Coffee\target\cafe-management.jar"
```

Kiểm tra các mục sau:

- Tạo OWNER, MANAGER, nhân viên; đổi mật khẩu tạm; menu theo vai trò; tạo OWNER thứ hai phải bị từ chối; vô hiệu hóa tài khoản đang đăng nhập thì quyền truy cập bị thu hồi.
- Tìm món, chọn bàn, chọn size/topping/ghi chú, sửa/xóa số lượng, đơn mang đi, chuyển/gộp bàn, thanh toán, tiền thừa, xem/in biên nhận và nhấn thanh toán lặp.
- Thanh toán tiền mặt/thẻ/chuyển khoản; chỉ tiền mặt ảnh hưởng quỹ. Đơn chưa thanh toán không tính vào doanh thu. Thanh toán lỗi hoặc thiếu tồn không được để lại một phần hóa đơn hay biến động tiền.
- Mở ca, ghi thêm/chi/rút, hoàn tiền mặt, đóng ca và giải thích chênh lệch. Ví dụ: `500.000 + 1.200.000 − 50.000 + 100.000 − 80.000 − 200.000 = 1.470.000 VND`; nếu đếm `1.450.000 VND`, chênh lệch là `−20.000 VND`. Xác nhận thanh toán không dùng tiền mặt không cộng vào quỹ; thao tác lặp không tạo trùng; ca đã đóng không nhận thanh toán.
- Với OWNER/MANAGER, kiểm tra lịch sử ca đã đóng và điều chỉnh có lý do; số gốc không đổi và audit được ghi. Với EMPLOYEE, thử xem báo cáo ngày cũ và ca của người khác.
- So sánh báo cáo ngày/lịch sử với thanh toán và hoàn tiền; khởi động lại với cùng file H2 tạm và xác nhận dữ liệu còn nguyên.
- Thử máy chủ MySQL chỉ trên database kiểm tra được cấp riêng. H2 ở chế độ tương thích không thay thế xác minh MySQL thật. SQL Server hiện chưa được dự án hỗ trợ.
- Kiểm tra hộp thoại in, khổ giấy và máy in nhiệt riêng trên máy đích.

Không trỏ cấu hình này đến `./data/cafe` hoặc database vận hành. Xóa dữ liệu UAT chỉ theo quy trình dọn môi trường thử nghiệm riêng của bạn.