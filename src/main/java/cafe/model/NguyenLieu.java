package cafe.model;

import java.math.BigDecimal;

public record NguyenLieu(Integer maNL, String tenNL, String donViTinh, BigDecimal soLuongTon,
                         BigDecimal mucCanhBao, boolean trangThai) {

    public boolean sapHet() {
        return soLuongTon.compareTo(mucCanhBao) <= 0;
    }

    @Override
    public String toString() {
        return tenNL + " (" + donViTinh + ")";
    }
}
