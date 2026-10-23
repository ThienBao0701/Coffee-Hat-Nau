package cafe.dao;

import cafe.model.ChiTietHoaDon;
import cafe.model.ChiTietTopping;
import cafe.model.HoaDon;
import cafe.model.LoaiDon;
import cafe.model.PhuongThucThanhToan;
import cafe.model.ThanhToan;
import cafe.model.TrangThaiHoaDon;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Invoices, invoice lines, line toppings and payments. */
public class InvoiceDao {

    /** Minimal locked view of an invoice used to check its state inside a transaction. */
    public record LockedInvoice(int maHD, TrangThaiHoaDon trangThai, LoaiDon loaiDon, Integer maBan, int maNV) {
    }

    static HoaDon map(ResultSet rs) throws SQLException {
        return new HoaDon(rs.getInt("ma_hd"), Jdbc.dateTime(rs, "ngay_lap"), LoaiDon.valueOf(rs.getString("loai_don")),
                TrangThaiHoaDon.valueOf(rs.getString("trang_thai")), Jdbc.intOrNull(rs, "ma_ban"), rs.getString("ten_ban"),
                Jdbc.intOrNull(rs, "ma_kh"), rs.getString("ten_kh"), rs.getInt("ma_nv"), rs.getString("ten_nv"),
                Jdbc.intOrNull(rs, "ma_km"), rs.getString("ten_km"), Jdbc.intOrNull(rs, "ma_thue"), rs.getBigDecimal("thue_suat"),
                rs.getBigDecimal("tam_tinh"), rs.getBigDecimal("tien_giam"), rs.getBigDecimal("tien_thue"), rs.getBigDecimal("tong_tien"),
                rs.getString("dia_chi_giao"), rs.getString("ghi_chu"), rs.getString("ly_do_huy"), Jdbc.dateTime(rs, "thoi_diem_huy"));
    }

    private static final String SELECT = "SELECT hd.ma_hd, hd.ngay_lap, hd.loai_don, hd.trang_thai, hd.ma_ban, b.ten_ban, hd.ma_kh, kh.ten_kh, "
            + "hd.ma_nv, nv.ten_nv, hd.ma_km, hd.ten_km, hd.ma_thue, hd.thue_suat, hd.tam_tinh, hd.tien_giam, hd.tien_thue, hd.tong_tien, "
            + "hd.dia_chi_giao, hd.ghi_chu, hd.ly_do_huy, hd.thoi_diem_huy FROM hoa_don hd "
            + "LEFT JOIN ban b ON b.ma_ban = hd.ma_ban LEFT JOIN khach_hang kh ON kh.ma_kh = hd.ma_kh "
            + "JOIN nhan_vien nv ON nv.ma_nv = hd.ma_nv ";

    public int insert(Connection c, LoaiDon loai, Integer maBan, Integer maKH, int maNV, String diaChiGiao, LocalDateTime now) throws SQLException {
        return Jdbc.insert(c, "INSERT INTO hoa_don (ngay_lap, loai_don, trang_thai, ma_ban, ma_kh, ma_nv, dia_chi_giao) VALUES (?, ?, 'OPEN', ?, ?, ?, ?)",
                now, loai, maBan, maKH, maNV, diaChiGiao);
    }

    public HoaDon findById(Connection c, int id) throws SQLException {
        return Jdbc.one(c, SELECT + "WHERE hd.ma_hd = ?", InvoiceDao::map, id);
    }

    /** Locks the invoice row (SELECT ... FOR UPDATE) and returns its state, or null if it does not exist. */
    public LockedInvoice lock(Connection c, int id) throws SQLException {
        return Jdbc.one(c, "SELECT ma_hd, trang_thai, loai_don, ma_ban, ma_nv FROM hoa_don WHERE ma_hd = ? FOR UPDATE",
                rs -> new LockedInvoice(rs.getInt(1), TrangThaiHoaDon.valueOf(rs.getString(2)), LoaiDon.valueOf(rs.getString(3)),
                        Jdbc.intOrNull(rs, "ma_ban"), rs.getInt(5)), id);
    }

    public Integer findOpenIdByTable(Connection c, int maBan) throws SQLException {
        return Jdbc.one(c, "SELECT ma_hd FROM hoa_don WHERE ma_ban = ? AND trang_thai = 'OPEN'", rs -> rs.getInt(1), maBan);
    }

    /** maBan -> open invoice id, for the table map. */
    public Map<Integer, Integer> findOpenByTable(Connection c) throws SQLException {
        Map<Integer, Integer> result = new HashMap<>();
        Jdbc.list(c, "SELECT ma_ban, ma_hd FROM hoa_don WHERE trang_thai = 'OPEN' AND ma_ban IS NOT NULL",
                rs -> result.put(rs.getInt(1), rs.getInt(2)));
        return result;
    }

    public List<HoaDon> findOpen(Connection c) throws SQLException {
        return Jdbc.list(c, SELECT + "WHERE hd.trang_thai = 'OPEN' ORDER BY hd.ngay_lap", InvoiceDao::map);
    }

    /** Invoices created in [from, to). Optional filters: status, creator, invoice id. */
    public List<HoaDon> search(Connection c, LocalDateTime from, LocalDateTime to, TrangThaiHoaDon status, Integer maNV, Integer maHD) throws SQLException {
        StringBuilder where = new StringBuilder("WHERE hd.ngay_lap >= ? AND hd.ngay_lap < ? ");
        List<Object> params = new ArrayList<>(List.of(from, to));
        if (status != null) {
            where.append("AND hd.trang_thai = ? ");
            params.add(status);
        }
        if (maNV != null) {
            // "mine" = created by me or paid at my till
            where.append("AND (hd.ma_nv = ? OR EXISTS (SELECT 1 FROM thanh_toan t WHERE t.ma_hd = hd.ma_hd AND t.ma_nv = ?)) ");
            params.add(maNV);
            params.add(maNV);
        }
        if (maHD != null) {
            where.append("AND hd.ma_hd = ? ");
            params.add(maHD);
        }
        return Jdbc.list(c, SELECT + where + "ORDER BY hd.ma_hd DESC", InvoiceDao::map, params.toArray());
    }

    // ---------- lines ----------
    public List<ChiTietHoaDon> findLines(Connection c, int maHD) throws SQLException {
        Map<Integer, List<ChiTietTopping>> toppings = new HashMap<>();
        Jdbc.list(c, "SELECT t.ma_cthd, t.ma_topping, t.ten_topping, t.so_luong, t.gia_them FROM chi_tiet_topping t "
                        + "JOIN chi_tiet_hoa_don ct ON ct.ma_cthd = t.ma_cthd WHERE ct.ma_hd = ? ORDER BY t.ten_topping",
                rs -> toppings.computeIfAbsent(rs.getInt(1), k -> new ArrayList<>())
                        .add(new ChiTietTopping(rs.getInt(2), rs.getString(3), rs.getInt(4), rs.getBigDecimal(5))), maHD);
        return Jdbc.list(c, "SELECT ma_cthd, ma_hd, ma_mon, ma_size, ten_mon, ten_size, so_luong, don_gia, ghi_chu "
                        + "FROM chi_tiet_hoa_don WHERE ma_hd = ? ORDER BY ma_cthd",
                rs -> new ChiTietHoaDon(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4), rs.getString(5), rs.getString(6),
                        rs.getInt(7), rs.getBigDecimal(8), rs.getString(9), toppings.getOrDefault(rs.getInt(1), List.of())), maHD);
    }

    public Integer findInvoiceIdOfLine(Connection c, int maCTHD) throws SQLException {
        return Jdbc.one(c, "SELECT ma_hd FROM chi_tiet_hoa_don WHERE ma_cthd = ?", rs -> rs.getInt(1), maCTHD);
    }

    public int insertLine(Connection c, int maHD, int maMon, int maSize, String tenMon, String tenSize, int soLuong,
                          BigDecimal donGia, String ghiChu) throws SQLException {
        return Jdbc.insert(c, "INSERT INTO chi_tiet_hoa_don (ma_hd, ma_mon, ma_size, ten_mon, ten_size, so_luong, don_gia, ghi_chu) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)", maHD, maMon, maSize, tenMon, tenSize, soLuong, donGia, ghiChu);
    }

    public void insertLineTopping(Connection c, int maCTHD, int maTopping, String ten, int soLuong, BigDecimal gia) throws SQLException {
        Jdbc.update(c, "INSERT INTO chi_tiet_topping (ma_cthd, ma_topping, ten_topping, so_luong, gia_them) VALUES (?, ?, ?, ?, ?)",
                maCTHD, maTopping, ten, soLuong, gia);
    }

    public void deleteLineToppings(Connection c, int maCTHD) throws SQLException {
        Jdbc.update(c, "DELETE FROM chi_tiet_topping WHERE ma_cthd = ?", maCTHD);
    }

    public void updateLine(Connection c, int maCTHD, int soLuong, String ghiChu) throws SQLException {
        Jdbc.update(c, "UPDATE chi_tiet_hoa_don SET so_luong = ?, ghi_chu = ? WHERE ma_cthd = ?", soLuong, ghiChu, maCTHD);
    }

    public void deleteLine(Connection c, int maCTHD) throws SQLException {
        Jdbc.update(c, "DELETE FROM chi_tiet_hoa_don WHERE ma_cthd = ?", maCTHD);
    }

    public void moveLines(Connection c, int fromHD, int toHD) throws SQLException {
        Jdbc.update(c, "UPDATE chi_tiet_hoa_don SET ma_hd = ? WHERE ma_hd = ?", toHD, fromHD);
    }

    // ---------- state changes ----------
    public void updateTable(Connection c, int maHD, int maBan) throws SQLException {
        Jdbc.update(c, "UPDATE hoa_don SET ma_ban = ? WHERE ma_hd = ?", maBan, maHD);
    }

    /** OPEN -> PAID with amount snapshots. Returns rows changed (0 if it was not OPEN any more). */
    public int markPaid(Connection c, int maHD, Integer maKH, Integer maKM, String tenKM, Integer maThue, BigDecimal thueSuat,
                        BigDecimal tamTinh, BigDecimal tienGiam, BigDecimal tienThue, BigDecimal tongTien) throws SQLException {
        return Jdbc.update(c, "UPDATE hoa_don SET trang_thai = 'PAID', ma_kh = ?, ma_km = ?, ten_km = ?, ma_thue = ?, thue_suat = ?, "
                        + "tam_tinh = ?, tien_giam = ?, tien_thue = ?, tong_tien = ? WHERE ma_hd = ? AND trang_thai = 'OPEN'",
                maKH, maKM, tenKM, maThue, thueSuat, tamTinh, tienGiam, tienThue, tongTien, maHD);
    }

    /** from -> to (CANCELLED or REFUNDED) with reason. Returns rows changed. */
    public int close(Connection c, int maHD, TrangThaiHoaDon from, TrangThaiHoaDon to, String reason, int maNV, LocalDateTime now) throws SQLException {
        return Jdbc.update(c, "UPDATE hoa_don SET trang_thai = ?, ly_do_huy = ?, ma_nv_huy = ?, thoi_diem_huy = ? WHERE ma_hd = ? AND trang_thai = ?",
                to, reason, maNV, now, maHD, from);
    }

    public void updateNote(Connection c, int maHD, String ghiChu) throws SQLException {
        Jdbc.update(c, "UPDATE hoa_don SET ghi_chu = ? WHERE ma_hd = ?", ghiChu, maHD);
    }

    // ---------- payments ----------
    public void insertPayment(Connection c, int maHD, PhuongThucThanhToan pt, BigDecimal soTien, BigDecimal khachDua,
                              BigDecimal tienThoi, LocalDateTime now, int maNV, int maCa) throws SQLException {
        Jdbc.insert(c, "INSERT INTO thanh_toan (ma_hd, phuong_thuc, so_tien, tien_khach_dua, tien_thoi, thoi_diem, ma_nv, ma_ca) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)", maHD, pt, soTien, khachDua, tienThoi, now, maNV, maCa);
    }

    public void insertRefund(Connection c, int maHD, Integer maCa, int employeeId, PhuongThucThanhToan method,
                             BigDecimal amount, LocalDateTime now, String reason) throws SQLException {
        Jdbc.insert(c, "INSERT INTO hoan_tien (ma_hd,ma_ca,ma_nv,phuong_thuc,so_tien,thoi_diem,ly_do) VALUES (?,?,?,?,?,?,?)",
                maHD, maCa, employeeId, method, amount, now, reason);
    }

    public ThanhToan findPayment(Connection c, int maHD) throws SQLException {
        return Jdbc.one(c, "SELECT t.ma_tt, t.ma_hd, t.phuong_thuc, t.so_tien, t.tien_khach_dua, t.tien_thoi, t.thoi_diem, t.ma_nv, nv.ten_nv "
                        + "FROM thanh_toan t JOIN nhan_vien nv ON nv.ma_nv = t.ma_nv WHERE t.ma_hd = ?",
                rs -> new ThanhToan(rs.getInt(1), rs.getInt(2), PhuongThucThanhToan.valueOf(rs.getString(3)), rs.getBigDecimal(4),
                        rs.getBigDecimal(5), rs.getBigDecimal(6), Jdbc.dateTime(rs, "thoi_diem"), rs.getInt(8), rs.getString(9)), maHD);
    }
}
