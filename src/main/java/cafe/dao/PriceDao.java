package cafe.dao;

import cafe.model.BangGia;
import cafe.model.ChiTietBangGia;
import cafe.model.MenuItem;
import cafe.model.Mon;
import cafe.model.Size;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Price lists and their product/size prices. */
public class PriceDao {

    static BangGia mapBangGia(ResultSet rs) throws SQLException {
        return new BangGia(rs.getInt("ma_bang_gia"), rs.getString("mo_ta"), Jdbc.date(rs, "ngay_bat_dau"), Jdbc.date(rs, "ngay_ket_thuc"));
    }

    private static final String BG_SELECT = "SELECT ma_bang_gia, mo_ta, ngay_bat_dau, ngay_ket_thuc FROM bang_gia ";

    public List<BangGia> findBangGia(Connection c) throws SQLException {
        return Jdbc.list(c, BG_SELECT + "ORDER BY ngay_bat_dau DESC", PriceDao::mapBangGia);
    }

    public BangGia findBangGiaById(Connection c, int id) throws SQLException {
        return Jdbc.one(c, BG_SELECT + "WHERE ma_bang_gia = ?", PriceDao::mapBangGia, id);
    }

    public BangGia findBangGiaOn(Connection c, LocalDate d) throws SQLException {
        return Jdbc.one(c, BG_SELECT + "WHERE ngay_bat_dau <= ? AND (ngay_ket_thuc IS NULL OR ngay_ket_thuc >= ?)",
                PriceDao::mapBangGia, d, d);
    }

    /** Price lists whose period intersects [start, end] (end null = open-ended). */
    public List<BangGia> findOverlapping(Connection c, LocalDate start, LocalDate end, Integer excludeId) throws SQLException {
        LocalDate effectiveEnd = end == null ? LocalDate.of(9999, 12, 31) : end;
        return Jdbc.list(c, BG_SELECT + "WHERE ma_bang_gia <> ? AND ngay_bat_dau <= ? AND (ngay_ket_thuc IS NULL OR ngay_ket_thuc >= ?)",
                PriceDao::mapBangGia, excludeId == null ? -1 : excludeId, effectiveEnd, start);
    }

    public int insertBangGia(Connection c, BangGia b) throws SQLException {
        return Jdbc.insert(c, "INSERT INTO bang_gia (mo_ta, ngay_bat_dau, ngay_ket_thuc) VALUES (?, ?, ?)",
                b.moTa(), b.ngayBatDau(), b.ngayKetThuc());
    }

    public void updateBangGia(Connection c, BangGia b) throws SQLException {
        Jdbc.update(c, "UPDATE bang_gia SET mo_ta = ?, ngay_bat_dau = ?, ngay_ket_thuc = ? WHERE ma_bang_gia = ?",
                b.moTa(), b.ngayBatDau(), b.ngayKetThuc(), b.maBangGia());
    }

    public void deleteBangGia(Connection c, int id) throws SQLException {
        Jdbc.update(c, "DELETE FROM bang_gia WHERE ma_bang_gia = ?", id);
    }

    public List<ChiTietBangGia> findChiTiet(Connection c, int maBangGia) throws SQLException {
        return Jdbc.list(c, "SELECT ct.ma_bang_gia, ct.ma_mon, m.ten_mon, ct.ma_size, s.ten_size, ct.gia_ban "
                        + "FROM chi_tiet_bang_gia ct JOIN mon m ON m.ma_mon = ct.ma_mon JOIN size_mon s ON s.ma_size = ct.ma_size "
                        + "WHERE ct.ma_bang_gia = ? ORDER BY m.ten_mon, s.thu_tu",
                rs -> new ChiTietBangGia(rs.getInt(1), rs.getInt(2), rs.getString(3), rs.getInt(4), rs.getString(5), rs.getBigDecimal(6)),
                maBangGia);
    }

    public void deleteChiTiet(Connection c, int maBangGia) throws SQLException {
        Jdbc.update(c, "DELETE FROM chi_tiet_bang_gia WHERE ma_bang_gia = ?", maBangGia);
    }

    public void insertChiTiet(Connection c, int maBangGia, int maMon, int maSize, BigDecimal gia) throws SQLException {
        Jdbc.update(c, "INSERT INTO chi_tiet_bang_gia (ma_bang_gia, ma_mon, ma_size, gia_ban) VALUES (?, ?, ?, ?)",
                maBangGia, maMon, maSize, gia);
    }

    /** Selling price of a product/size on a date, or null if no price list in effect defines it. */
    public BigDecimal findPrice(Connection c, int maMon, int maSize, LocalDate d) throws SQLException {
        return Jdbc.one(c, "SELECT ct.gia_ban FROM chi_tiet_bang_gia ct JOIN bang_gia bg ON bg.ma_bang_gia = ct.ma_bang_gia "
                        + "WHERE ct.ma_mon = ? AND ct.ma_size = ? AND bg.ngay_bat_dau <= ? AND (bg.ngay_ket_thuc IS NULL OR bg.ngay_ket_thuc >= ?)",
                rs -> rs.getBigDecimal(1), maMon, maSize, d, d);
    }

    /** Active products that have at least one price today, with their prices per size. */
    public List<MenuItem> findMenu(Connection c, LocalDate d) throws SQLException {
        Map<Integer, Mon> mons = new LinkedHashMap<>();
        Map<Integer, List<MenuItem.SizePrice>> prices = new LinkedHashMap<>();
        Jdbc.list(c, "SELECT m.ma_mon, m.ten_mon, m.ma_loai, l.ten_loai, m.trang_thai, "
                        + "s.ma_size, s.ten_size, s.he_so_dinh_luong, s.thu_tu, ct.gia_ban "
                        + "FROM chi_tiet_bang_gia ct "
                        + "JOIN bang_gia bg ON bg.ma_bang_gia = ct.ma_bang_gia "
                        + "JOIN mon m ON m.ma_mon = ct.ma_mon JOIN loai_mon l ON l.ma_loai = m.ma_loai "
                        + "JOIN size_mon s ON s.ma_size = ct.ma_size "
                        + "WHERE m.trang_thai = TRUE AND bg.ngay_bat_dau <= ? AND (bg.ngay_ket_thuc IS NULL OR bg.ngay_ket_thuc >= ?) "
                        + "ORDER BY l.ten_loai, m.ten_mon, s.thu_tu",
                rs -> {
                    Mon m = CatalogDao.mapMon(rs);
                    Size s = CatalogDao.mapSize(rs);
                    mons.putIfAbsent(m.maMon(), m);
                    prices.computeIfAbsent(m.maMon(), k -> new ArrayList<>()).add(new MenuItem.SizePrice(s, rs.getBigDecimal("gia_ban")));
                    return null;
                }, d, d);
        List<MenuItem> menu = new ArrayList<>();
        mons.forEach((id, m) -> menu.add(new MenuItem(m, prices.get(id))));
        return menu;
    }
}
