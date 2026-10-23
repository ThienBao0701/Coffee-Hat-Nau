package cafe.dao;

import cafe.model.NhatKy;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

public class AuditDao {

    public void insert(Connection c, LocalDateTime now, Integer maNV, String hanhDong, String doiTuong, String maDoiTuong, String chiTiet)
            throws SQLException {
        Jdbc.insert(c, "INSERT INTO nhat_ky (thoi_diem, ma_nv, hanh_dong, doi_tuong, ma_doi_tuong, chi_tiet) VALUES (?, ?, ?, ?, ?, ?)",
                now, maNV, hanhDong, doiTuong, maDoiTuong, chiTiet);
    }

    public List<NhatKy> find(Connection c, LocalDateTime from, LocalDateTime to) throws SQLException {
        return Jdbc.list(c, "SELECT nk.ma_nk, nk.thoi_diem, nv.ten_nv, nk.hanh_dong, nk.doi_tuong, nk.ma_doi_tuong, nk.chi_tiet "
                        + "FROM nhat_ky nk LEFT JOIN nhan_vien nv ON nv.ma_nv = nk.ma_nv "
                        + "WHERE nk.thoi_diem >= ? AND nk.thoi_diem < ? ORDER BY nk.ma_nk DESC",
                rs -> new NhatKy(rs.getInt(1), Jdbc.dateTime(rs, "thoi_diem"), rs.getString(3), rs.getString(4), rs.getString(5),
                        rs.getString(6), rs.getString(7)), from, to);
    }
}
