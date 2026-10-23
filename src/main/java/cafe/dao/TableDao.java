package cafe.dao;

import cafe.model.Ban;
import cafe.model.KhuVuc;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/** Areas and tables. */
public class TableDao {

    public List<KhuVuc> findKhuVuc(Connection c) throws SQLException {
        return Jdbc.list(c, "SELECT ma_khu_vuc, ten_khu_vuc FROM khu_vuc ORDER BY ma_khu_vuc",
                rs -> new KhuVuc(rs.getInt(1), rs.getString(2)));
    }

    public boolean khuVucNameExists(Connection c, String ten, Integer excludeId) throws SQLException {
        return Jdbc.count(c, "SELECT COUNT(*) FROM khu_vuc WHERE LOWER(ten_khu_vuc) = LOWER(?) AND ma_khu_vuc <> ?",
                ten, excludeId == null ? -1 : excludeId) > 0;
    }

    public int insertKhuVuc(Connection c, String ten) throws SQLException {
        return Jdbc.insert(c, "INSERT INTO khu_vuc (ten_khu_vuc) VALUES (?)", ten);
    }

    public void updateKhuVuc(Connection c, KhuVuc k) throws SQLException {
        Jdbc.update(c, "UPDATE khu_vuc SET ten_khu_vuc = ? WHERE ma_khu_vuc = ?", k.tenKhuVuc(), k.maKhuVuc());
    }

    public int countBanInKhuVuc(Connection c, int maKhuVuc) throws SQLException {
        return Jdbc.count(c, "SELECT COUNT(*) FROM ban WHERE ma_khu_vuc = ?", maKhuVuc);
    }

    public void deleteKhuVuc(Connection c, int id) throws SQLException {
        Jdbc.update(c, "DELETE FROM khu_vuc WHERE ma_khu_vuc = ?", id);
    }

    static Ban mapBan(ResultSet rs) throws SQLException {
        return new Ban(rs.getInt("ma_ban"), rs.getString("ten_ban"), rs.getInt("ma_khu_vuc"), rs.getString("ten_khu_vuc"),
                rs.getInt("so_cho"), rs.getBoolean("trang_thai"));
    }

    private static final String BAN_SELECT = "SELECT b.ma_ban, b.ten_ban, b.ma_khu_vuc, k.ten_khu_vuc, b.so_cho, b.trang_thai "
            + "FROM ban b JOIN khu_vuc k ON k.ma_khu_vuc = b.ma_khu_vuc ";

    public List<Ban> findBan(Connection c, boolean activeOnly) throws SQLException {
        return Jdbc.list(c, BAN_SELECT + (activeOnly ? "WHERE b.trang_thai = TRUE " : "") + "ORDER BY k.ma_khu_vuc, b.ten_ban",
                TableDao::mapBan);
    }

    public Ban findBanById(Connection c, int id) throws SQLException {
        return Jdbc.one(c, BAN_SELECT + "WHERE b.ma_ban = ?", TableDao::mapBan, id);
    }

    /** Row lock on the table: serialises "open order on this table" between terminals. */
    public void lockBan(Connection c, int id) throws SQLException {
        Jdbc.one(c, "SELECT ma_ban FROM ban WHERE ma_ban = ? FOR UPDATE", rs -> rs.getInt(1), id);
    }

    public boolean banNameExists(Connection c, String ten, Integer excludeId) throws SQLException {
        return Jdbc.count(c, "SELECT COUNT(*) FROM ban WHERE LOWER(ten_ban) = LOWER(?) AND ma_ban <> ?",
                ten, excludeId == null ? -1 : excludeId) > 0;
    }

    public int insertBan(Connection c, Ban b) throws SQLException {
        return Jdbc.insert(c, "INSERT INTO ban (ten_ban, ma_khu_vuc, so_cho, trang_thai) VALUES (?, ?, ?, ?)",
                b.tenBan(), b.maKhuVuc(), b.soCho(), b.trangThai());
    }

    public void updateBan(Connection c, Ban b) throws SQLException {
        Jdbc.update(c, "UPDATE ban SET ten_ban = ?, ma_khu_vuc = ?, so_cho = ?, trang_thai = ? WHERE ma_ban = ?",
                b.tenBan(), b.maKhuVuc(), b.soCho(), b.trangThai(), b.maBan());
    }
}
