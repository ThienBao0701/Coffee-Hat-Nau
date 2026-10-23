package cafe.model;

import java.math.BigDecimal;

public record ChiTietPhieuXuat(int maNL, String tenNL, String donViTinh, BigDecimal soLuongXuat, BigDecimal donGiaXuat) {
    public BigDecimal thanhTien() {
        return soLuongXuat.multiply(donGiaXuat);
    }
}
