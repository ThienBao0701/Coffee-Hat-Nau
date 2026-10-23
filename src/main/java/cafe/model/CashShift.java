package cafe.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Immutable cashier-shift and closeout snapshot. */
public record CashShift(int maCa, int maNV, String tenNV, LocalDateTime moLuc, BigDecimal tienDauCa,
                        String trangThai, LocalDateTime dongLuc, BigDecimal tienDem,
                        BigDecimal tienKyVong, BigDecimal chenhLech, String ghiChuDong) {
    public boolean dangMo() {
        return "OPEN".equals(trangThai);
    }
}
