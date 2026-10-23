package cafe.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Payment record ("bangThanhToan" in the UML). One per invoice. */
public record ThanhToan(Integer maTT, int maHD, PhuongThucThanhToan phuongThuc, BigDecimal soTien,
                        BigDecimal tienKhachDua, BigDecimal tienThoi, LocalDateTime thoiDiem, int maNV, String tenNV) {
}
