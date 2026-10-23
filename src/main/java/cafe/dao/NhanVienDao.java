package cafe.dao;

import cafe.model.NhanVien;
import cafe.model.Role;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class NhanVienDao {

    /** Employee plus password hash; used only for authentication. */
    public record Credentials(NhanVien nhanVien, String passwordHash) {
    }

    private static final String COLS = "ma_nv, ten_nv, chuc_vu, so_dien_thoai, ngay_sinh, dia_chi, bang_cap, gio_lam, "
            + "he_so_luong, luong_co_ban, ten_dang_nhap, vai_tro, chu_so_huu, phai_doi_mat_khau, trang_thai";

    static NhanVien map(ResultSet rs) throws SQLException {
        return new NhanVien(rs.getInt("ma_nv"), rs.getString("ten_nv"), rs.getString("chuc_vu"),
                rs.getString("so_dien_thoai"), Jdbc.date(rs, "ngay_sinh"), rs.getString("dia_chi"),
                rs.getString("bang_cap"), rs.getString("gio_lam"), rs.getBigDecimal("he_so_luong"),
                rs.getBigDecimal("luong_co_ban"), rs.getString("ten_dang_nhap"),
                rs.getBoolean("chu_so_huu") ? Role.CHU_CUA_HANG : Role.valueOf(rs.getString("vai_tro")),
                rs.getBoolean("phai_doi_mat_khau"), rs.getBoolean("trang_thai"));
    }

    public List<NhanVien> findAll(Connection c) throws SQLException {
        return Jdbc.list(c, "SELECT " + COLS + " FROM nhan_vien ORDER BY trang_thai DESC, ten_nv", NhanVienDao::map);
    }

    public NhanVien findById(Connection c, int id) throws SQLException {
        return Jdbc.one(c, "SELECT " + COLS + " FROM nhan_vien WHERE ma_nv = ?", NhanVienDao::map, id);
    }

    public Credentials findCredentials(Connection c, String username) throws SQLException {
        return Jdbc.one(c, "SELECT " + COLS + ", mat_khau FROM nhan_vien WHERE ten_dang_nhap = ?",
                rs -> new Credentials(map(rs), rs.getString("mat_khau")), username);
    }

    public String findPasswordHash(Connection c, int id) throws SQLException {
        return Jdbc.one(c, "SELECT mat_khau FROM nhan_vien WHERE ma_nv = ?", rs -> rs.getString(1), id);
    }

    public boolean usernameExists(Connection c, String username, Integer excludeId) throws SQLException {
        return Jdbc.count(c, "SELECT COUNT(*) FROM nhan_vien WHERE LOWER(ten_dang_nhap) = LOWER(?) AND ma_nv <> ?",
                username, excludeId == null ? -1 : excludeId) > 0;
    }

    public int countActiveManagers(Connection c) throws SQLException {
        return Jdbc.count(c, "SELECT COUNT(*) FROM nhan_vien WHERE vai_tro = 'QUAN_LY' AND trang_thai = TRUE");
    }

    public int countActiveOwners(Connection c) throws SQLException {
        return Jdbc.count(c, "SELECT COUNT(*) FROM nhan_vien WHERE chu_so_huu = TRUE AND trang_thai = TRUE");
    }

    public int insert(Connection c, NhanVien nv, String passwordHash) throws SQLException {
        return Jdbc.insert(c, "INSERT INTO nhan_vien (ten_nv, chuc_vu, so_dien_thoai, ngay_sinh, dia_chi, bang_cap, gio_lam, "
                        + "he_so_luong, luong_co_ban, ten_dang_nhap, mat_khau, vai_tro, chu_so_huu, phai_doi_mat_khau, trang_thai) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                nv.tenNV(), nv.chucVu(), nv.soDienThoai(), nv.ngaySinh(), nv.diaChi(), nv.bangCap(), nv.gioLam(),
                nv.heSoLuong(), nv.luongCoBan(), nv.tenDangNhap(), passwordHash,
                nv.vaiTro() == Role.CHU_CUA_HANG ? Role.QUAN_LY : nv.vaiTro(), nv.vaiTro() == Role.CHU_CUA_HANG,
                nv.phaiDoiMatKhau(), nv.trangThai());
    }

    public void update(Connection c, NhanVien nv) throws SQLException {
        Jdbc.update(c, "UPDATE nhan_vien SET ten_nv = ?, chuc_vu = ?, so_dien_thoai = ?, ngay_sinh = ?, dia_chi = ?, bang_cap = ?, "
                        + "gio_lam = ?, he_so_luong = ?, luong_co_ban = ?, ten_dang_nhap = ?, vai_tro = ?, chu_so_huu = ?, trang_thai = ? WHERE ma_nv = ?",
                nv.tenNV(), nv.chucVu(), nv.soDienThoai(), nv.ngaySinh(), nv.diaChi(), nv.bangCap(), nv.gioLam(),
                nv.heSoLuong(), nv.luongCoBan(), nv.tenDangNhap(),
                nv.vaiTro() == Role.CHU_CUA_HANG ? Role.QUAN_LY : nv.vaiTro(), nv.vaiTro() == Role.CHU_CUA_HANG,
                nv.trangThai(), nv.maNV());
    }

    public void promoteOwner(Connection c, int id) throws SQLException {
        Jdbc.update(c, "UPDATE nhan_vien SET chu_so_huu = TRUE, vai_tro = 'QUAN_LY' WHERE ma_nv = ?", id);
    }

    public void updatePassword(Connection c, int id, String hash, boolean mustChange) throws SQLException {
        Jdbc.update(c, "UPDATE nhan_vien SET mat_khau = ?, phai_doi_mat_khau = ? WHERE ma_nv = ?", hash, mustChange, id);
    }
}
