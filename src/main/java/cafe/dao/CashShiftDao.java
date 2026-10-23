package cafe.dao;

import cafe.model.CashShift;
import cafe.model.CashShiftAdjustment;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class CashShiftDao {
    public record Totals(BigDecimal cashSales, BigDecimal cashRefunds, BigDecimal additions,
                         BigDecimal expenses, BigDecimal withdrawals) {
        public BigDecimal expected(BigDecimal opening) {
            return opening.add(cashSales).subtract(cashRefunds).add(additions).subtract(expenses).subtract(withdrawals);
        }
    }

    private static final String SELECT = "SELECT c.ma_ca,c.ma_nv,n.ten_nv,c.mo_luc,c.tien_dau_ca,c.trang_thai,"
            + "c.dong_luc,c.tien_dem,c.tien_ky_vong,c.chenh_lech,c.ghi_chu_dong FROM ca_thu_ngan c "
            + "JOIN nhan_vien n ON n.ma_nv=c.ma_nv ";

    public Integer lockActiveId(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT ma_ca_dang_mo FROM ngan_ke_toan WHERE ma_ngan=1 FOR UPDATE");
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) throw new SQLException("Missing cash drawer singleton row");
            int id = rs.getInt(1);
            return rs.wasNull() ? null : id;
        }
    }

    public Integer activeId(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT ma_ca_dang_mo FROM ngan_ke_toan WHERE ma_ngan=1");
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) return null;
            int id = rs.getInt(1);
            return rs.wasNull() ? null : id;
        }
    }

    public CashShift find(Connection c, int id) throws SQLException {
        return Jdbc.one(c, SELECT + "WHERE c.ma_ca=?", CashShiftDao::map, id);
    }

    public CashShift findActiveForEmployee(Connection c, int employeeId) throws SQLException {
        return Jdbc.one(c, SELECT + "JOIN ngan_ke_toan k ON k.ma_ca_dang_mo=c.ma_ca WHERE c.ma_nv=? AND c.trang_thai='OPEN'",
                CashShiftDao::map, employeeId);
    }

    public List<CashShift> findRecent(Connection c, int employeeId, boolean all) throws SQLException {
        String sql = SELECT + (all ? "" : "WHERE c.ma_nv=? ") + "ORDER BY c.mo_luc DESC";
        return all ? Jdbc.list(c, sql, CashShiftDao::map) : Jdbc.list(c, sql, CashShiftDao::map, employeeId);
    }

    public int open(Connection c, int employeeId, BigDecimal opening, LocalDateTime now) throws SQLException {
        int id = Jdbc.insert(c, "INSERT INTO ca_thu_ngan (ma_nv,mo_luc,tien_dau_ca,trang_thai) VALUES (?,?,?,'OPEN')",
                employeeId, now, opening);
        int changed = Jdbc.update(c, "UPDATE ngan_ke_toan SET ma_ca_dang_mo=? WHERE ma_ngan=1 AND ma_ca_dang_mo IS NULL", id);
        if (changed != 1) throw new SQLException("Cash drawer is already assigned");
        return id;
    }

    public int insertMovement(Connection c, int shiftId, int employeeId, LocalDateTime now, String kind,
                              BigDecimal amount, String reason, String requestKey) throws SQLException {
        return Jdbc.insert(c, "INSERT INTO bien_dong_tien_mat (ma_ca,ma_nv,thoi_diem,loai,so_tien,ly_do,ma_yeu_cau) VALUES (?,?,?,?,?,?,?)",
                shiftId, employeeId, now, kind, amount, reason, requestKey);
    }

    public boolean movementMatches(Connection c, String requestKey, int shiftId, String kind,
                                   BigDecimal amount) throws SQLException {
        if (requestKey == null) return false;
        try (PreparedStatement ps = c.prepareStatement("SELECT ma_ca,loai,so_tien FROM bien_dong_tien_mat WHERE ma_yeu_cau=?")) {
            ps.setString(1, requestKey);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return false;
                if (rs.getInt(1) != shiftId || !kind.equals(rs.getString(2)) || rs.getBigDecimal(3).compareTo(amount) != 0) {
                    throw new cafe.util.BusinessException("Mã yêu cầu đã được dùng cho một biến động khác.");
                }
                return true;
            }
        }
    }

    public List<CashShiftAdjustment> adjustments(Connection c, int shiftId) throws SQLException {
        return Jdbc.list(c, "SELECT d.ma_dc,d.ma_ca,d.ma_nv,n.ten_nv,d.thoi_diem,d.tac_dong_ky_vong,d.ly_do,d.ma_yeu_cau "
                        + "FROM dieu_chinh_ca d JOIN nhan_vien n ON n.ma_nv=d.ma_nv WHERE d.ma_ca=? ORDER BY d.thoi_diem DESC,d.ma_dc DESC",
                rs -> new CashShiftAdjustment(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getString(4),
                        Jdbc.dateTime(rs, "thoi_diem"), rs.getBigDecimal(6), rs.getString(7), rs.getString(8)), shiftId);
    }

    public List<CashShiftAdjustment> adjustments(Connection c) throws SQLException {
        return Jdbc.list(c, "SELECT d.ma_dc,d.ma_ca,d.ma_nv,n.ten_nv,d.thoi_diem,d.tac_dong_ky_vong,d.ly_do,d.ma_yeu_cau "
                        + "FROM dieu_chinh_ca d JOIN nhan_vien n ON n.ma_nv=d.ma_nv ORDER BY d.thoi_diem DESC,d.ma_dc DESC",
                rs -> new CashShiftAdjustment(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getString(4),
                        Jdbc.dateTime(rs, "thoi_diem"), rs.getBigDecimal(6), rs.getString(7), rs.getString(8)));
    }

    public BigDecimal adjustmentTotal(Connection c, int shiftId) throws SQLException {
        return Jdbc.one(c, "SELECT COALESCE(SUM(tac_dong_ky_vong),0) FROM dieu_chinh_ca WHERE ma_ca=?",
                rs -> rs.getBigDecimal(1), shiftId);
    }

    public boolean adjustmentMatches(Connection c, String requestKey, int shiftId, BigDecimal impact) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT ma_ca,tac_dong_ky_vong FROM dieu_chinh_ca WHERE ma_yeu_cau=?")) {
            ps.setString(1, requestKey);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return false;
                if (rs.getInt(1) != shiftId || rs.getBigDecimal(2).compareTo(impact) != 0) {
                    throw new cafe.util.BusinessException("Mã yêu cầu đã được dùng cho một điều chỉnh khác.");
                }
                return true;
            }
        }
    }

    public int insertAdjustment(Connection c, int shiftId, int employeeId, LocalDateTime now,
                                BigDecimal impact, String reason, String requestKey) throws SQLException {
        return Jdbc.insert(c, "INSERT INTO dieu_chinh_ca (ma_ca,ma_nv,thoi_diem,tac_dong_ky_vong,ly_do,ma_yeu_cau) VALUES (?,?,?,?,?,?)",
                shiftId, employeeId, now, impact, reason, requestKey);
    }

    public boolean lockShift(Connection c, int shiftId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT ma_ca FROM ca_thu_ngan WHERE ma_ca=? FOR UPDATE")) {
            ps.setInt(1, shiftId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    public Totals totals(Connection c, int shiftId) throws SQLException {
        BigDecimal cashSales = sum(c, "SELECT COALESCE(SUM(so_tien),0) FROM thanh_toan WHERE ma_ca=? AND phuong_thuc='TIEN_MAT'", shiftId);
        BigDecimal cashRefunds = sum(c, "SELECT COALESCE(SUM(so_tien),0) FROM hoan_tien WHERE ma_ca=? AND phuong_thuc='TIEN_MAT'", shiftId);
        BigDecimal additions = sumMovement(c, shiftId, "THEM");
        BigDecimal expenses = sumMovement(c, shiftId, "CHI");
        BigDecimal withdrawals = sumMovement(c, shiftId, "RUT");
        return new Totals(cashSales, cashRefunds, additions, expenses, withdrawals);
    }

    private static BigDecimal sum(Connection c, String sql, int shiftId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, shiftId);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getBigDecimal(1); }
        }
    }

    private static BigDecimal sumMovement(Connection c, int shiftId, String kind) throws SQLException {
        return sum(c, "SELECT COALESCE(SUM(so_tien),0) FROM bien_dong_tien_mat WHERE ma_ca=? AND loai='" + kind + "'", shiftId);
    }

    public int countOpenOrders(Connection c, int employeeId) throws SQLException {
        return Jdbc.count(c, "SELECT COUNT(*) FROM hoa_don WHERE ma_nv=? AND trang_thai='OPEN'", employeeId);
    }

    public void close(Connection c, int shiftId, int actor, LocalDateTime now, BigDecimal counted,
                      BigDecimal expected, BigDecimal variance, String note) throws SQLException {
        int changed = Jdbc.update(c, "UPDATE ca_thu_ngan SET trang_thai='CLOSED',dong_luc=?,tien_dem=?,tien_ky_vong=?,"
                        + "chenh_lech=?,ghi_chu_dong=?,ma_nv_dong=? WHERE ma_ca=? AND trang_thai='OPEN'",
                now, counted, expected, variance, note, actor, shiftId);
        if (changed != 1) throw new cafe.util.BusinessException("Ca đã được đóng hoặc không còn hoạt động.");
        Jdbc.update(c, "UPDATE ngan_ke_toan SET ma_ca_dang_mo=NULL WHERE ma_ngan=1 AND ma_ca_dang_mo=?", shiftId);
    }

    private static CashShift map(ResultSet rs) throws SQLException {
        return new CashShift(rs.getInt(1), rs.getInt(2), rs.getString(3), Jdbc.dateTime(rs, "mo_luc"), rs.getBigDecimal(5),
                rs.getString(6), Jdbc.dateTime(rs, "dong_luc"), rs.getBigDecimal(8), rs.getBigDecimal(9), rs.getBigDecimal(10),
                rs.getString(11));
    }
}
