package cafe.dao;

import cafe.model.LoaiMon;
import cafe.model.Mon;
import cafe.model.Size;
import cafe.model.Topping;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/** Categories, products, sizes and toppings. */
public class CatalogDao {

    // ---------- categories ----------
    public List<LoaiMon> findLoaiMon(Connection c) throws SQLException {
        return Jdbc.list(c, "SELECT ma_loai, ten_loai FROM loai_mon ORDER BY ten_loai",
                rs -> new LoaiMon(rs.getInt(1), rs.getString(2)));
    }

    public int insertLoaiMon(Connection c, String ten) throws SQLException {
        return Jdbc.insert(c, "INSERT INTO loai_mon (ten_loai) VALUES (?)", ten);
    }

    public void updateLoaiMon(Connection c, LoaiMon l) throws SQLException {
        Jdbc.update(c, "UPDATE loai_mon SET ten_loai = ? WHERE ma_loai = ?", l.tenLoai(), l.maLoai());
    }

    public int countMonInLoai(Connection c, int maLoai) throws SQLException {
        return Jdbc.count(c, "SELECT COUNT(*) FROM mon WHERE ma_loai = ?", maLoai);
    }

    public void deleteLoaiMon(Connection c, int maLoai) throws SQLException {
        Jdbc.update(c, "DELETE FROM loai_mon WHERE ma_loai = ?", maLoai);
    }

    public boolean loaiMonNameExists(Connection c, String ten, Integer excludeId) throws SQLException {
        return Jdbc.count(c, "SELECT COUNT(*) FROM loai_mon WHERE LOWER(ten_loai) = LOWER(?) AND ma_loai <> ?",
                ten, excludeId == null ? -1 : excludeId) > 0;
    }

    // ---------- products ----------
    static Mon mapMon(ResultSet rs) throws SQLException {
        return new Mon(rs.getInt("ma_mon"), rs.getString("ten_mon"), rs.getInt("ma_loai"), rs.getString("ten_loai"),
                rs.getBoolean("trang_thai"));
    }

    private static final String MON_SELECT = "SELECT m.ma_mon, m.ten_mon, m.ma_loai, l.ten_loai, m.trang_thai "
            + "FROM mon m JOIN loai_mon l ON l.ma_loai = m.ma_loai ";

    public List<Mon> findMon(Connection c, boolean activeOnly) throws SQLException {
        return Jdbc.list(c, MON_SELECT + (activeOnly ? "WHERE m.trang_thai = TRUE " : "") + "ORDER BY l.ten_loai, m.ten_mon",
                CatalogDao::mapMon);
    }

    public Mon findMonById(Connection c, int id) throws SQLException {
        return Jdbc.one(c, MON_SELECT + "WHERE m.ma_mon = ?", CatalogDao::mapMon, id);
    }

    public boolean monNameExists(Connection c, String ten, Integer excludeId) throws SQLException {
        return Jdbc.count(c, "SELECT COUNT(*) FROM mon WHERE LOWER(ten_mon) = LOWER(?) AND ma_mon <> ?",
                ten, excludeId == null ? -1 : excludeId) > 0;
    }

    public int insertMon(Connection c, Mon m) throws SQLException {
        return Jdbc.insert(c, "INSERT INTO mon (ten_mon, ma_loai, trang_thai) VALUES (?, ?, ?)", m.tenMon(), m.maLoai(), m.trangThai());
    }

    public void updateMon(Connection c, Mon m) throws SQLException {
        Jdbc.update(c, "UPDATE mon SET ten_mon = ?, ma_loai = ?, trang_thai = ? WHERE ma_mon = ?",
                m.tenMon(), m.maLoai(), m.trangThai(), m.maMon());
    }

    // ---------- sizes ----------
    static Size mapSize(ResultSet rs) throws SQLException {
        return new Size(rs.getInt("ma_size"), rs.getString("ten_size"), rs.getBigDecimal("he_so_dinh_luong"), rs.getInt("thu_tu"));
    }

    public List<Size> findSizes(Connection c) throws SQLException {
        return Jdbc.list(c, "SELECT ma_size, ten_size, he_so_dinh_luong, thu_tu FROM size_mon ORDER BY thu_tu, ten_size", CatalogDao::mapSize);
    }

    public Size findSizeById(Connection c, int id) throws SQLException {
        return Jdbc.one(c, "SELECT ma_size, ten_size, he_so_dinh_luong, thu_tu FROM size_mon WHERE ma_size = ?", CatalogDao::mapSize, id);
    }

    public boolean sizeNameExists(Connection c, String ten, Integer excludeId) throws SQLException {
        return Jdbc.count(c, "SELECT COUNT(*) FROM size_mon WHERE LOWER(ten_size) = LOWER(?) AND ma_size <> ?",
                ten, excludeId == null ? -1 : excludeId) > 0;
    }

    public int insertSize(Connection c, Size s) throws SQLException {
        return Jdbc.insert(c, "INSERT INTO size_mon (ten_size, he_so_dinh_luong, thu_tu) VALUES (?, ?, ?)",
                s.tenSize(), s.heSoDinhLuong(), s.thuTu());
    }

    public void updateSize(Connection c, Size s) throws SQLException {
        Jdbc.update(c, "UPDATE size_mon SET ten_size = ?, he_so_dinh_luong = ?, thu_tu = ? WHERE ma_size = ?",
                s.tenSize(), s.heSoDinhLuong(), s.thuTu(), s.maSize());
    }

    // ---------- toppings ----------
    static Topping mapTopping(ResultSet rs) throws SQLException {
        return new Topping(rs.getInt("ma_topping"), rs.getString("ten_topping"), rs.getBigDecimal("gia_them"), rs.getBoolean("trang_thai"));
    }

    public List<Topping> findToppings(Connection c, boolean activeOnly) throws SQLException {
        return Jdbc.list(c, "SELECT ma_topping, ten_topping, gia_them, trang_thai FROM topping "
                + (activeOnly ? "WHERE trang_thai = TRUE " : "") + "ORDER BY ten_topping", CatalogDao::mapTopping);
    }

    public Topping findToppingById(Connection c, int id) throws SQLException {
        return Jdbc.one(c, "SELECT ma_topping, ten_topping, gia_them, trang_thai FROM topping WHERE ma_topping = ?",
                CatalogDao::mapTopping, id);
    }

    public boolean toppingNameExists(Connection c, String ten, Integer excludeId) throws SQLException {
        return Jdbc.count(c, "SELECT COUNT(*) FROM topping WHERE LOWER(ten_topping) = LOWER(?) AND ma_topping <> ?",
                ten, excludeId == null ? -1 : excludeId) > 0;
    }

    public int insertTopping(Connection c, Topping t) throws SQLException {
        return Jdbc.insert(c, "INSERT INTO topping (ten_topping, gia_them, trang_thai) VALUES (?, ?, ?)",
                t.tenTopping(), t.giaThem(), t.trangThai());
    }

    public void updateTopping(Connection c, Topping t) throws SQLException {
        Jdbc.update(c, "UPDATE topping SET ten_topping = ?, gia_them = ?, trang_thai = ? WHERE ma_topping = ?",
                t.tenTopping(), t.giaThem(), t.trangThai(), t.maTopping());
    }
}
