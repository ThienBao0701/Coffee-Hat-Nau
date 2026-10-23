package cafe.dao;

import cafe.model.Reports;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/** Read-only report queries. Periods are half-open [from, to). See Reports for revenue definitions. */
public class ReportDao {

    /** One paid sale (status PAID or later REFUNDED), dated by its payment time. */
    public record PaidInvoice(int maHD, LocalDateTime thoiDiem, int maNV, String tenNV, int maThuNgan, String tenThuNgan,
                              String phuongThuc, BigDecimal tamTinh, BigDecimal tienGiam, BigDecimal tienThue, BigDecimal tongTien) {
    }

    public List<PaidInvoice> findPaidInvoices(Connection c, LocalDateTime from, LocalDateTime to) throws SQLException {
        return Jdbc.list(c, "SELECT hd.ma_hd, t.thoi_diem, hd.ma_nv, creator.ten_nv, t.ma_nv, cashier.ten_nv, t.phuong_thuc, "
                        + "hd.tam_tinh, hd.tien_giam, hd.tien_thue, hd.tong_tien "
                        + "FROM hoa_don hd JOIN thanh_toan t ON t.ma_hd = hd.ma_hd JOIN nhan_vien creator ON creator.ma_nv = hd.ma_nv "
                        + "JOIN nhan_vien cashier ON cashier.ma_nv = t.ma_nv "
                        + "WHERE hd.trang_thai IN ('PAID', 'REFUNDED') AND t.thoi_diem >= ? AND t.thoi_diem < ? ORDER BY t.thoi_diem",
                rs -> new PaidInvoice(rs.getInt(1), Jdbc.dateTime(rs, "thoi_diem"), rs.getInt(3), rs.getString(4), rs.getInt(5),
                        rs.getString(6), rs.getString(7), rs.getBigDecimal(8), rs.getBigDecimal(9), rs.getBigDecimal(10),
                        rs.getBigDecimal(11)), from, to);
    }

    /** [count, pre-tax refunded amount] of invoices refunded in the period. */
    public Object[] findRefunds(Connection c, LocalDateTime from, LocalDateTime to) throws SQLException {
        return Jdbc.one(c, "SELECT COUNT(*), COALESCE(SUM(tam_tinh - tien_giam), 0) FROM hoa_don "
                        + "WHERE trang_thai = 'REFUNDED' AND thoi_diem_huy >= ? AND thoi_diem_huy < ?",
                rs -> new Object[]{rs.getInt(1), rs.getBigDecimal(2)}, from, to);
    }

    public List<Reports.ProductSales> findProductSales(Connection c, LocalDateTime from, LocalDateTime to) throws SQLException {
        return Jdbc.list(c, "SELECT m.ten_mon, SUM(ct.so_luong) AS sl, "
                        + "SUM(ct.so_luong * (ct.don_gia + COALESCE((SELECT SUM(tp.gia_them * tp.so_luong) FROM chi_tiet_topping tp "
                        + "WHERE tp.ma_cthd = ct.ma_cthd), 0))) AS doanh_so "
                        + "FROM chi_tiet_hoa_don ct JOIN hoa_don hd ON hd.ma_hd = ct.ma_hd JOIN thanh_toan t ON t.ma_hd = hd.ma_hd "
                        + "JOIN mon m ON m.ma_mon = ct.ma_mon "
                        + "WHERE hd.trang_thai IN ('PAID', 'REFUNDED') AND t.thoi_diem >= ? AND t.thoi_diem < ? "
                        + "GROUP BY m.ma_mon, m.ten_mon ORDER BY sl DESC, doanh_so DESC",
                rs -> new Reports.ProductSales(rs.getString(1), rs.getInt(2), rs.getBigDecimal(3)), from, to);
    }

    public int countCancelled(Connection c, LocalDateTime from, LocalDateTime to) throws SQLException {
        return Jdbc.count(c, "SELECT COUNT(*) FROM hoa_don WHERE trang_thai='CANCELLED' AND thoi_diem_huy >= ? AND thoi_diem_huy < ?", from, to);
    }

    public Object[] collectedByCashier(Connection c, int employeeId, LocalDateTime from, LocalDateTime to) throws SQLException {
        return Jdbc.one(c, "SELECT COUNT(*),COALESCE(SUM(t.so_tien),0) FROM thanh_toan t "
                        + "JOIN hoa_don hd ON hd.ma_hd=t.ma_hd WHERE t.ma_nv=? AND hd.trang_thai IN ('PAID','REFUNDED') "
                        + "AND t.thoi_diem>=? AND t.thoi_diem<?",
                rs -> new Object[]{rs.getInt(1), rs.getBigDecimal(2)}, employeeId, from, to);
    }

    public List<Reports.PaymentMethodTotal> paymentMethods(Connection c, LocalDateTime from, LocalDateTime to) throws SQLException {
        return Jdbc.list(c, "SELECT t.phuong_thuc,COUNT(*),COALESCE(SUM(t.so_tien),0) FROM thanh_toan t "
                        + "JOIN hoa_don hd ON hd.ma_hd=t.ma_hd WHERE hd.trang_thai IN ('PAID','REFUNDED') "
                        + "AND t.thoi_diem>=? AND t.thoi_diem<? GROUP BY t.phuong_thuc ORDER BY t.phuong_thuc",
                rs -> new Reports.PaymentMethodTotal(rs.getString(1), rs.getInt(2), rs.getBigDecimal(3)), from, to);
    }

    public List<Reports.CashierSales> cashierSales(Connection c, LocalDateTime from, LocalDateTime to) throws SQLException {
        return Jdbc.list(c, "SELECT t.ma_nv,nv.ten_nv,COUNT(*),COALESCE(SUM(t.so_tien),0) FROM thanh_toan t "
                        + "JOIN hoa_don hd ON hd.ma_hd=t.ma_hd JOIN nhan_vien nv ON nv.ma_nv=t.ma_nv "
                        + "WHERE hd.trang_thai IN ('PAID','REFUNDED') AND t.thoi_diem>=? AND t.thoi_diem<? "
                        + "GROUP BY t.ma_nv,nv.ten_nv ORDER BY SUM(t.so_tien) DESC",
                rs -> new Reports.CashierSales(rs.getInt(1), rs.getString(2), rs.getInt(3), rs.getBigDecimal(4)), from, to);
    }

    public List<Reports.TransactionRow> transactions(Connection c, LocalDateTime from, LocalDateTime to) throws SQLException {
        return transactions(c, from, to, null, null);
    }

    public List<Reports.TransactionRow> transactions(Connection c, LocalDateTime from, LocalDateTime to,
                                                      Integer employeeId, String paymentMethod) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT hd.ma_hd,t.thoi_diem,hd.trang_thai,hd.ma_nv,creator.ten_nv,t.ma_nv,cashier.ten_nv,"
                        + "t.phuong_thuc,t.so_tien FROM hoa_don hd JOIN thanh_toan t ON t.ma_hd=hd.ma_hd "
                        + "JOIN nhan_vien creator ON creator.ma_nv=hd.ma_nv JOIN nhan_vien cashier ON cashier.ma_nv=t.ma_nv "
                        + "WHERE t.thoi_diem>=? AND t.thoi_diem<?");
        List<Object> params = new java.util.ArrayList<>(List.of(from, to));
        if (employeeId != null) {
            sql.append(" AND hd.ma_nv=?");
            params.add(employeeId);
        }
        if (paymentMethod != null) {
            sql.append(" AND t.phuong_thuc=?");
            params.add(paymentMethod);
        }
        sql.append(" ORDER BY t.thoi_diem DESC");
        return Jdbc.list(c, sql.toString(),
                rs -> new Reports.TransactionRow(rs.getInt(1), Jdbc.dateTime(rs, "thoi_diem").toString(), rs.getString(3),
                        rs.getInt(4), rs.getString(5), rs.getInt(6), rs.getString(7), rs.getString(8), rs.getBigDecimal(9)),
                params.toArray());
    }

    /** [ingredient name, unit, current stock, movements after 'to', inflow in period, outflow in period]. */
    public List<Object[]> findStockFigures(Connection c, LocalDateTime from, LocalDateTime to) throws SQLException {
        return Jdbc.list(c, "SELECT nl.ten_nl, nl.don_vi_tinh, nl.so_luong_ton, "
                        + "COALESCE((SELECT SUM(so_luong) FROM bien_dong_kho WHERE ma_nl = nl.ma_nl AND thoi_diem >= ?), 0), "
                        + "COALESCE((SELECT SUM(so_luong) FROM bien_dong_kho WHERE ma_nl = nl.ma_nl AND thoi_diem >= ? AND thoi_diem < ? AND so_luong > 0), 0), "
                        + "COALESCE((SELECT SUM(so_luong) FROM bien_dong_kho WHERE ma_nl = nl.ma_nl AND thoi_diem >= ? AND thoi_diem < ? AND so_luong < 0), 0) "
                        + "FROM nguyen_lieu nl ORDER BY nl.ten_nl",
                rs -> new Object[]{rs.getString(1), rs.getString(2), rs.getBigDecimal(3), rs.getBigDecimal(4), rs.getBigDecimal(5), rs.getBigDecimal(6)},
                to, from, to, from, to);
    }
}
