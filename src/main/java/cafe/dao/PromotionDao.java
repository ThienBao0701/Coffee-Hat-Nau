package cafe.dao;

import cafe.model.KhuyenMai;
import cafe.model.LoaiKhuyenMai;
import cafe.model.Thue;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/** Promotion types, promotions and taxes. */
public class PromotionDao {

    // ---------- promotion types ----------
    static LoaiKhuyenMai mapLoai(ResultSet rs) throws SQLException {
        return new LoaiKhuyenMai(rs.getInt("ma_loai_km"), rs.getString("ten_loai_km"), rs.getBigDecimal("phan_tram_giam"),
                rs.getBigDecimal("phan_tien_giam"));
    }

    public List<LoaiKhuyenMai> findLoai(Connection c) throws SQLException {
        return Jdbc.list(c, "SELECT ma_loai_km, ten_loai_km, phan_tram_giam, phan_tien_giam FROM loai_khuyen_mai ORDER BY ten_loai_km",
                PromotionDao::mapLoai);
    }

    public int insertLoai(Connection c, LoaiKhuyenMai l) throws SQLException {
        return Jdbc.insert(c, "INSERT INTO loai_khuyen_mai (ten_loai_km, phan_tram_giam, phan_tien_giam) VALUES (?, ?, ?)",
                l.tenLoaiKM(), l.phanTramGiam(), l.phanTienGiam());
    }

    public void updateLoai(Connection c, LoaiKhuyenMai l) throws SQLException {
        Jdbc.update(c, "UPDATE loai_khuyen_mai SET ten_loai_km = ?, phan_tram_giam = ?, phan_tien_giam = ? WHERE ma_loai_km = ?",
                l.tenLoaiKM(), l.phanTramGiam(), l.phanTienGiam(), l.maLoaiKM());
    }

    public int countKhuyenMaiOfLoai(Connection c, int maLoai) throws SQLException {
        return Jdbc.count(c, "SELECT COUNT(*) FROM khuyen_mai WHERE ma_loai_km = ?", maLoai);
    }

    public void deleteLoai(Connection c, int maLoai) throws SQLException {
        Jdbc.update(c, "DELETE FROM loai_khuyen_mai WHERE ma_loai_km = ?", maLoai);
    }

    // ---------- promotions ----------
    static KhuyenMai mapKM(ResultSet rs) throws SQLException {
        return new KhuyenMai(rs.getInt("ma_km"), rs.getString("ten_km"), rs.getInt("ma_loai_km"), rs.getString("ten_loai_km"),
                rs.getBigDecimal("phan_tram_giam"), rs.getBigDecimal("phan_tien_giam"), Jdbc.date(rs, "ngay_bat_dau"),
                Jdbc.date(rs, "ngay_ket_thuc"), rs.getBigDecimal("gia_tri_don_toi_thieu"), rs.getBigDecimal("giam_toi_da"),
                rs.getBoolean("trang_thai"));
    }

    private static final String KM_SELECT = "SELECT km.ma_km, km.ten_km, km.ma_loai_km, l.ten_loai_km, l.phan_tram_giam, l.phan_tien_giam, "
            + "km.ngay_bat_dau, km.ngay_ket_thuc, km.gia_tri_don_toi_thieu, km.giam_toi_da, km.trang_thai "
            + "FROM khuyen_mai km JOIN loai_khuyen_mai l ON l.ma_loai_km = km.ma_loai_km ";

    public List<KhuyenMai> findKhuyenMai(Connection c) throws SQLException {
        return Jdbc.list(c, KM_SELECT + "ORDER BY km.ngay_bat_dau DESC", PromotionDao::mapKM);
    }

    public KhuyenMai findKhuyenMaiById(Connection c, int id) throws SQLException {
        return Jdbc.one(c, KM_SELECT + "WHERE km.ma_km = ?", PromotionDao::mapKM, id);
    }

    public List<KhuyenMai> findKhuyenMaiActiveOn(Connection c, LocalDate d) throws SQLException {
        return Jdbc.list(c, KM_SELECT + "WHERE km.trang_thai = TRUE AND km.ngay_bat_dau <= ? AND km.ngay_ket_thuc >= ? ORDER BY km.ten_km",
                PromotionDao::mapKM, d, d);
    }

    public int insertKhuyenMai(Connection c, KhuyenMai k) throws SQLException {
        return Jdbc.insert(c, "INSERT INTO khuyen_mai (ten_km, ma_loai_km, ngay_bat_dau, ngay_ket_thuc, gia_tri_don_toi_thieu, giam_toi_da, trang_thai) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                k.tenKM(), k.maLoaiKM(), k.ngayBatDau(), k.ngayKetThuc(), k.giaTriDonToiThieu(), k.giamToiDa(), k.trangThai());
    }

    public void updateKhuyenMai(Connection c, KhuyenMai k) throws SQLException {
        Jdbc.update(c, "UPDATE khuyen_mai SET ten_km = ?, ma_loai_km = ?, ngay_bat_dau = ?, ngay_ket_thuc = ?, gia_tri_don_toi_thieu = ?, "
                        + "giam_toi_da = ?, trang_thai = ? WHERE ma_km = ?",
                k.tenKM(), k.maLoaiKM(), k.ngayBatDau(), k.ngayKetThuc(), k.giaTriDonToiThieu(), k.giamToiDa(), k.trangThai(), k.maKM());
    }

    // ---------- taxes ----------
    static Thue mapThue(ResultSet rs) throws SQLException {
        return new Thue(rs.getInt("ma_thue"), rs.getString("ten_thue"), rs.getBigDecimal("phan_tram"),
                Jdbc.date(rs, "ngay_bat_dau"), Jdbc.date(rs, "ngay_ket_thuc"));
    }

    private static final String THUE_SELECT = "SELECT ma_thue, ten_thue, phan_tram, ngay_bat_dau, ngay_ket_thuc FROM thue ";

    public List<Thue> findThue(Connection c) throws SQLException {
        return Jdbc.list(c, THUE_SELECT + "ORDER BY ngay_bat_dau DESC", PromotionDao::mapThue);
    }

    public Thue findThueOn(Connection c, LocalDate d) throws SQLException {
        return Jdbc.one(c, THUE_SELECT + "WHERE ngay_bat_dau <= ? AND (ngay_ket_thuc IS NULL OR ngay_ket_thuc >= ?)",
                PromotionDao::mapThue, d, d);
    }

    public List<Thue> findThueOverlapping(Connection c, LocalDate start, LocalDate end, Integer excludeId) throws SQLException {
        LocalDate effectiveEnd = end == null ? LocalDate.of(9999, 12, 31) : end;
        return Jdbc.list(c, THUE_SELECT + "WHERE ma_thue <> ? AND ngay_bat_dau <= ? AND (ngay_ket_thuc IS NULL OR ngay_ket_thuc >= ?)",
                PromotionDao::mapThue, excludeId == null ? -1 : excludeId, effectiveEnd, start);
    }

    public int insertThue(Connection c, Thue t) throws SQLException {
        return Jdbc.insert(c, "INSERT INTO thue (ten_thue, phan_tram, ngay_bat_dau, ngay_ket_thuc) VALUES (?, ?, ?, ?)",
                t.tenThue(), t.phanTram(), t.ngayBatDau(), t.ngayKetThuc());
    }

    public void updateThue(Connection c, Thue t) throws SQLException {
        Jdbc.update(c, "UPDATE thue SET ten_thue = ?, phan_tram = ?, ngay_bat_dau = ?, ngay_ket_thuc = ? WHERE ma_thue = ?",
                t.tenThue(), t.phanTram(), t.ngayBatDau(), t.ngayKetThuc(), t.maThue());
    }

    public int countInvoicesWithThue(Connection c, int maThue) throws SQLException {
        return Jdbc.count(c, "SELECT COUNT(*) FROM hoa_don WHERE ma_thue = ?", maThue);
    }
}
