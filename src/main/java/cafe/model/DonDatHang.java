package cafe.model;

import java.time.LocalDateTime;

/** Table reservation. */
public record DonDatHang(Integer maDDH, int maKH, String tenKH, String sdtKH, int maBan, String tenBan,
                         int maNV, String tenNV, LocalDateTime thoiDiemDat, int soNguoi, String ghiChu,
                         TrangThaiDatBan trangThai, LocalDateTime ngayTao) {
}
