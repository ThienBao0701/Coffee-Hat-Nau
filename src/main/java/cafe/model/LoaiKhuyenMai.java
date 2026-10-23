package cafe.model;

import cafe.util.Money;

import java.math.BigDecimal;

/** Discount kind: EITHER phanTramGiam (percent) OR phanTienGiam (fixed amount). */
public record LoaiKhuyenMai(Integer maLoaiKM, String tenLoaiKM, BigDecimal phanTramGiam, BigDecimal phanTienGiam) {

    public String moTaGiam() {
        return phanTramGiam != null ? phanTramGiam.stripTrailingZeros().toPlainString() + "%" : Money.format(phanTienGiam);
    }

    @Override
    public String toString() {
        return tenLoaiKM + " (" + moTaGiam() + ")";
    }
}
