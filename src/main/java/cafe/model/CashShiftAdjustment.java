package cafe.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Append-only, management-authorized correction to a closed shift's expected-cash calculation. */
public record CashShiftAdjustment(int maDieuChinh, int maCa, int maNV, String tenNV,
                                  LocalDateTime thoiDiem, BigDecimal tacDongKyVong,
                                  String lyDo, String maYeuCau) {
}
