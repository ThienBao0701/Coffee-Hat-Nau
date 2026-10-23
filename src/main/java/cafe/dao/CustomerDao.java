package cafe.dao;

import cafe.model.DonDatHang;
import cafe.model.KhachHang;
import cafe.model.TrangThaiDatBan;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Customers and table reservations. */
public class CustomerDao {

    static KhachHang mapKH(ResultSet rs) throws SQLException {
        return new KhachHang(rs.getInt("ma_kh"), rs.getString("ten_kh"), Jdbc.date(rs, "ngay_sinh"), rs.getString("so_cmnd"),
                rs.getString("so_dien_thoai"), rs.getString("dia_chi"));
    }

    private static final String KH_SELECT = "SELECT ma_kh, ten_kh, ngay_sinh, so_cmnd, so_dien_thoai, dia_chi FROM khach_hang ";

    /** Search by name or phone (blank = all). */
    public List<KhachHang> search(Connection c, String keyword) throws SQLException {
        if (keyword == null || keyword.isBlank()) {
            return Jdbc.list(c, KH_SELECT + "ORDER BY ten_kh", CustomerDao::mapKH);
        }
        String like = "%" + keyword.trim().toLowerCase() + "%";
        return Jdbc.list(c, KH_SELECT + "WHERE LOWER(ten_kh) LIKE ? OR so_dien_thoai LIKE ? ORDER BY ten_kh",
                CustomerDao::mapKH, like, like);
    }

    public KhachHang findById(Connection c, int id) throws SQLException {
        return Jdbc.one(c, KH_SELECT + "WHERE ma_kh = ?", CustomerDao::mapKH, id);
    }

    public KhachHang findByPhone(Connection c, String phone) throws SQLException {
        return Jdbc.one(c, KH_SELECT + "WHERE so_dien_thoai = ?", CustomerDao::mapKH, phone);
    }

    public boolean phoneExists(Connection c, String phone, Integer excludeId) throws SQLException {
        return Jdbc.count(c, "SELECT COUNT(*) FROM khach_hang WHERE so_dien_thoai = ? AND ma_kh <> ?", phone, excludeId == null ? -1 : excludeId) > 0;
    }

    public boolean cmndExists(Connection c, String cmnd, Integer excludeId) throws SQLException {
        return Jdbc.count(c, "SELECT COUNT(*) FROM khach_hang WHERE so_cmnd = ? AND ma_kh <> ?", cmnd, excludeId == null ? -1 : excludeId) > 0;
    }

    public int insert(Connection c, KhachHang k) throws SQLException {
        return Jdbc.insert(c, "INSERT INTO khach_hang (ten_kh, ngay_sinh, so_cmnd, so_dien_thoai, dia_chi) VALUES (?, ?, ?, ?, ?)",
                k.tenKH(), k.ngaySinh(), k.soCMND(), k.soDienThoai(), k.diaChi());
    }

    public void update(Connection c, KhachHang k) throws SQLException {
        Jdbc.update(c, "UPDATE khach_hang SET ten_kh = ?, ngay_sinh = ?, so_cmnd = ?, so_dien_thoai = ?, dia_chi = ? WHERE ma_kh = ?",
                k.tenKH(), k.ngaySinh(), k.soCMND(), k.soDienThoai(), k.diaChi(), k.maKH());
    }

    public int countReferences(Connection c, int maKH) throws SQLException {
        return Jdbc.count(c, "SELECT (SELECT COUNT(*) FROM hoa_don WHERE ma_kh = ?) + (SELECT COUNT(*) FROM don_dat_hang WHERE ma_kh = ?)",
                maKH, maKH);
    }

    public void delete(Connection c, int maKH) throws SQLException {
        Jdbc.update(c, "DELETE FROM khach_hang WHERE ma_kh = ?", maKH);
    }

    // ---------- reservations ----------
    static DonDatHang mapDDH(ResultSet rs) throws SQLException {
        return new DonDatHang(rs.getInt("ma_ddh"), rs.getInt("ma_kh"), rs.getString("ten_kh"), rs.getString("so_dien_thoai"),
                rs.getInt("ma_ban"), rs.getString("ten_ban"), rs.getInt("ma_nv"), rs.getString("ten_nv"),
                Jdbc.dateTime(rs, "thoi_diem_dat"), rs.getInt("so_nguoi"), rs.getString("ghi_chu"),
                TrangThaiDatBan.valueOf(rs.getString("trang_thai")), Jdbc.dateTime(rs, "ngay_tao"));
    }

    private static final String DDH_SELECT = "SELECT d.ma_ddh, d.ma_kh, k.ten_kh, k.so_dien_thoai, d.ma_ban, b.ten_ban, d.ma_nv, nv.ten_nv, "
            + "d.thoi_diem_dat, d.so_nguoi, d.ghi_chu, d.trang_thai, d.ngay_tao FROM don_dat_hang d "
            + "JOIN khach_hang k ON k.ma_kh = d.ma_kh JOIN ban b ON b.ma_ban = d.ma_ban JOIN nhan_vien nv ON nv.ma_nv = d.ma_nv ";

    /** Reservations in [from, to); status null = all. */
    public List<DonDatHang> findReservations(Connection c, LocalDateTime from, LocalDateTime to, TrangThaiDatBan status) throws SQLException {
        List<Object> params = new ArrayList<>(List.of(from, to));
        String where = "WHERE d.thoi_diem_dat >= ? AND d.thoi_diem_dat < ? ";
        if (status != null) {
            where += "AND d.trang_thai = ? ";
            params.add(status);
        }
        return Jdbc.list(c, DDH_SELECT + where + "ORDER BY d.thoi_diem_dat", CustomerDao::mapDDH, params.toArray());
    }

    public DonDatHang findReservation(Connection c, int id) throws SQLException {
        return Jdbc.one(c, DDH_SELECT + "WHERE d.ma_ddh = ?", CustomerDao::mapDDH, id);
    }

    /** Active (pending/confirmed) reservations of a table whose time falls in (from, to). */
    public List<DonDatHang> findActiveReservations(Connection c, int maBan, LocalDateTime from, LocalDateTime to, Integer excludeId) throws SQLException {
        return Jdbc.list(c, DDH_SELECT + "WHERE d.ma_ban = ? AND d.trang_thai IN ('CHO_XAC_NHAN', 'DA_XAC_NHAN') "
                        + "AND d.thoi_diem_dat > ? AND d.thoi_diem_dat < ? AND d.ma_ddh <> ? ORDER BY d.thoi_diem_dat",
                CustomerDao::mapDDH, maBan, from, to, excludeId == null ? -1 : excludeId);
    }

    /** All active reservations with time in [from, to] (for table status). */
    public List<DonDatHang> findActiveReservationsBetween(Connection c, LocalDateTime from, LocalDateTime to) throws SQLException {
        return Jdbc.list(c, DDH_SELECT + "WHERE d.trang_thai IN ('CHO_XAC_NHAN', 'DA_XAC_NHAN') AND d.thoi_diem_dat >= ? AND d.thoi_diem_dat <= ? "
                + "ORDER BY d.thoi_diem_dat", CustomerDao::mapDDH, from, to);
    }

    public int insertReservation(Connection c, DonDatHang d) throws SQLException {
        return Jdbc.insert(c, "INSERT INTO don_dat_hang (ma_kh, ma_ban, ma_nv, thoi_diem_dat, so_nguoi, ghi_chu, trang_thai, ngay_tao) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                d.maKH(), d.maBan(), d.maNV(), d.thoiDiemDat(), d.soNguoi(), d.ghiChu(), d.trangThai(), d.ngayTao());
    }

    /** Changes status only if the reservation is currently in one of the expected states. */
    public int updateReservationStatus(Connection c, int id, TrangThaiDatBan newStatus, TrangThaiDatBan... expected) throws SQLException {
        List<Object> params = new ArrayList<>();
        params.add(newStatus);
        params.add(id);
        for (TrangThaiDatBan e : expected) {
            params.add(e);
        }
        return Jdbc.update(c, "UPDATE don_dat_hang SET trang_thai = ? WHERE ma_ddh = ? AND trang_thai IN (" + Jdbc.placeholders(expected.length) + ")",
                params.toArray());
    }
}
