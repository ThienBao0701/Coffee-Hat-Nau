package cafe.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Employee and login account. The password hash (column mat_khau) is deliberately NOT part of
 * this object: it is only read by the authentication query and never travels to the UI.
 */
public record NhanVien(Integer maNV, String tenNV, String chucVu, String soDienThoai, LocalDate ngaySinh,
                       String diaChi, String bangCap, String gioLam, BigDecimal heSoLuong, BigDecimal luongCoBan,
                       String tenDangNhap, Role vaiTro, boolean phaiDoiMatKhau, boolean trangThai) {

    /** Monthly salary = base salary x coefficient. */
    public BigDecimal tinhLuong() {
        return luongCoBan.multiply(heSoLuong);
    }

    @Override
    public String toString() {
        return tenNV;
    }
}
