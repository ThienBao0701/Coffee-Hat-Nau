package cafe.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Promotion campaign. The discount value comes from its LoaiKhuyenMai (copied here when loaded). */
public record KhuyenMai(Integer maKM, String tenKM, int maLoaiKM, String tenLoaiKM, BigDecimal phanTramGiam,
                        BigDecimal phanTienGiam, LocalDate ngayBatDau, LocalDate ngayKetThuc,
                        BigDecimal giaTriDonToiThieu, BigDecimal giamToiDa, boolean trangThai) {

    public boolean hieuLucVao(LocalDate d) {
        return trangThai && !ngayBatDau.isAfter(d) && !ngayKetThuc.isBefore(d);
    }

    @Override
    public String toString() {
        return tenKM;
    }
}
