package cafe.model;

import java.time.LocalDateTime;

/** Audit log entry. */
public record NhatKy(Integer maNK, LocalDateTime thoiDiem, String tenNV, String hanhDong, String doiTuong,
                     String maDoiTuong, String chiTiet) {
}
