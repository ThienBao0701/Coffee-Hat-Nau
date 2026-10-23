package cafe.model;

import java.math.BigDecimal;

public record ChiTietPhieuNhap(int maNL, String tenNL, String donViTinh, BigDecimal soLuong, BigDecimal giaNhap) {
    public BigDecimal thanhTien() {
        return soLuong.multiply(giaNhap);
    }
}
