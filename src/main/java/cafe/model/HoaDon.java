package cafe.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Invoice header. tenKM, thueSuat and the four amounts are snapshots written at payment time,
 * so a paid invoice never changes when promotions, taxes or prices are edited later.
 */
public record HoaDon(Integer maHD, LocalDateTime ngayLap, LoaiDon loaiDon, TrangThaiHoaDon trangThai,
                     Integer maBan, String tenBan, Integer maKH, String tenKH, int maNV, String tenNV,
                     Integer maKM, String tenKM, Integer maThue, BigDecimal thueSuat,
                     BigDecimal tamTinh, BigDecimal tienGiam, BigDecimal tienThue, BigDecimal tongTien,
                     String diaChiGiao, String ghiChu, String lyDoHuy, LocalDateTime thoiDiemHuy) {

    /** Short label used in the UI, e.g. "HĐ 15 - Bàn 03". */
    public String nhan() {
        String where = maBan != null ? tenBan : loaiDon.toString();
        return "HĐ " + maHD + " - " + where;
    }
}
