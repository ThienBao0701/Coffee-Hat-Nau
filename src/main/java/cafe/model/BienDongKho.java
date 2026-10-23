package cafe.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One stock movement. soLuong is signed (+ in, - out); tonSau = stock after the movement. */
public record BienDongKho(Integer maBD, int maNL, String tenNL, LocalDateTime thoiDiem, LoaiBienDong loai,
                          BigDecimal soLuong, BigDecimal tonSau, String thamChieu, String tenNV, String ghiChu) {
}
