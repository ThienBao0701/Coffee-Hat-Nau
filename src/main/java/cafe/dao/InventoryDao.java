package cafe.dao;

import cafe.model.BienDongKho;
import cafe.model.ChiTietPhieuNhap;
import cafe.model.ChiTietPhieuXuat;
import cafe.model.CongThuc;
import cafe.model.LoaiBienDong;
import cafe.model.NguyenLieu;
import cafe.model.NhaCungCap;
import cafe.model.PhieuNhap;
import cafe.model.PhieuXuat;
import cafe.model.TrangThaiPhieu;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Ingredients, recipes, suppliers, stock receipts/issues and stock movements. */
public class InventoryDao {

    // ---------- ingredients ----------
    static NguyenLieu mapNL(ResultSet rs) throws SQLException {
        return new NguyenLieu(rs.getInt("ma_nl"), rs.getString("ten_nl"), rs.getString("don_vi_tinh"), rs.getBigDecimal("so_luong_ton"),
                rs.getBigDecimal("muc_canh_bao"), rs.getBoolean("trang_thai"));
    }

    private static final String NL_SELECT = "SELECT ma_nl, ten_nl, don_vi_tinh, so_luong_ton, muc_canh_bao, trang_thai FROM nguyen_lieu ";

    public List<NguyenLieu> findNguyenLieu(Connection c, boolean activeOnly) throws SQLException {
        return Jdbc.list(c, NL_SELECT + (activeOnly ? "WHERE trang_thai = TRUE " : "") + "ORDER BY ten_nl", InventoryDao::mapNL);
    }

    public NguyenLieu findNguyenLieuById(Connection c, int id) throws SQLException {
        return Jdbc.one(c, NL_SELECT + "WHERE ma_nl = ?", InventoryDao::mapNL, id);
    }

    public List<NguyenLieu> findLowStock(Connection c) throws SQLException {
        return Jdbc.list(c, NL_SELECT + "WHERE trang_thai = TRUE AND so_luong_ton <= muc_canh_bao ORDER BY ten_nl", InventoryDao::mapNL);
    }

    /** Locks the ingredient row and returns its current stock (null if missing). */
    public BigDecimal lockStock(Connection c, int maNL) throws SQLException {
        return Jdbc.one(c, "SELECT so_luong_ton FROM nguyen_lieu WHERE ma_nl = ? FOR UPDATE", rs -> rs.getBigDecimal(1), maNL);
    }

    public void setStock(Connection c, int maNL, BigDecimal qty) throws SQLException {
        Jdbc.update(c, "UPDATE nguyen_lieu SET so_luong_ton = ? WHERE ma_nl = ?", qty, maNL);
    }

    public boolean nguyenLieuNameExists(Connection c, String ten, Integer excludeId) throws SQLException {
        return Jdbc.count(c, "SELECT COUNT(*) FROM nguyen_lieu WHERE LOWER(ten_nl) = LOWER(?) AND ma_nl <> ?",
                ten, excludeId == null ? -1 : excludeId) > 0;
    }

    /** New ingredient starts at 0; opening stock is added through a TON_DAU movement. */
    public int insertNguyenLieu(Connection c, NguyenLieu n) throws SQLException {
        return Jdbc.insert(c, "INSERT INTO nguyen_lieu (ten_nl, don_vi_tinh, so_luong_ton, muc_canh_bao, trang_thai) VALUES (?, ?, 0, ?, ?)",
                n.tenNL(), n.donViTinh(), n.mucCanhBao(), n.trangThai());
    }

    /** Never touches so_luong_ton: stock only changes through movements. */
    public void updateNguyenLieu(Connection c, NguyenLieu n) throws SQLException {
        Jdbc.update(c, "UPDATE nguyen_lieu SET ten_nl = ?, don_vi_tinh = ?, muc_canh_bao = ?, trang_thai = ? WHERE ma_nl = ?",
                n.tenNL(), n.donViTinh(), n.mucCanhBao(), n.trangThai(), n.maNL());
    }

    // ---------- recipes ----------
    private static final String CT_SELECT = "SELECT ct.ma_ct, ct.ma_mon, m.ten_mon, ct.ma_nl, nl.ten_nl, nl.don_vi_tinh, ct.dinh_luong "
            + "FROM cong_thuc ct JOIN mon m ON m.ma_mon = ct.ma_mon JOIN nguyen_lieu nl ON nl.ma_nl = ct.ma_nl ";

    static CongThuc mapCT(ResultSet rs) throws SQLException {
        return new CongThuc(rs.getInt(1), rs.getInt(2), rs.getString(3), rs.getInt(4), rs.getString(5), rs.getString(6), rs.getBigDecimal(7));
    }

    public List<CongThuc> findCongThuc(Connection c, int maMon) throws SQLException {
        return Jdbc.list(c, CT_SELECT + "WHERE ct.ma_mon = ? ORDER BY nl.ten_nl", InventoryDao::mapCT, maMon);
    }

    public boolean congThucExists(Connection c, int maMon, int maNL, Integer excludeId) throws SQLException {
        return Jdbc.count(c, "SELECT COUNT(*) FROM cong_thuc WHERE ma_mon = ? AND ma_nl = ? AND ma_ct <> ?",
                maMon, maNL, excludeId == null ? -1 : excludeId) > 0;
    }

    public int insertCongThuc(Connection c, int maMon, int maNL, BigDecimal dinhLuong) throws SQLException {
        return Jdbc.insert(c, "INSERT INTO cong_thuc (ma_mon, ma_nl, dinh_luong) VALUES (?, ?, ?)", maMon, maNL, dinhLuong);
    }

    public void updateCongThuc(Connection c, int maCT, int maNL, BigDecimal dinhLuong) throws SQLException {
        Jdbc.update(c, "UPDATE cong_thuc SET ma_nl = ?, dinh_luong = ? WHERE ma_ct = ?", maNL, dinhLuong, maCT);
    }

    public void deleteCongThuc(Connection c, int maCT) throws SQLException {
        Jdbc.update(c, "DELETE FROM cong_thuc WHERE ma_ct = ?", maCT);
    }

    // ---------- suppliers ----------
    static NhaCungCap mapNCC(ResultSet rs) throws SQLException {
        return new NhaCungCap(rs.getInt("ma_ncc"), rs.getString("ten_ncc"), rs.getString("so_dien_thoai"), rs.getString("dia_chi"),
                rs.getString("email"), rs.getString("ghi_chu"), rs.getBoolean("trang_thai"));
    }

    public List<NhaCungCap> findNhaCungCap(Connection c, boolean activeOnly) throws SQLException {
        return Jdbc.list(c, "SELECT ma_ncc, ten_ncc, so_dien_thoai, dia_chi, email, ghi_chu, trang_thai FROM nha_cung_cap "
                + (activeOnly ? "WHERE trang_thai = TRUE " : "") + "ORDER BY ten_ncc", InventoryDao::mapNCC);
    }

    public boolean nhaCungCapNameExists(Connection c, String ten, Integer excludeId) throws SQLException {
        return Jdbc.count(c, "SELECT COUNT(*) FROM nha_cung_cap WHERE LOWER(ten_ncc) = LOWER(?) AND ma_ncc <> ?",
                ten, excludeId == null ? -1 : excludeId) > 0;
    }

    public int insertNhaCungCap(Connection c, NhaCungCap n) throws SQLException {
        return Jdbc.insert(c, "INSERT INTO nha_cung_cap (ten_ncc, so_dien_thoai, dia_chi, email, ghi_chu, trang_thai) VALUES (?, ?, ?, ?, ?, ?)",
                n.tenNCC(), n.soDienThoai(), n.diaChi(), n.email(), n.ghiChu(), n.trangThai());
    }

    public void updateNhaCungCap(Connection c, NhaCungCap n) throws SQLException {
        Jdbc.update(c, "UPDATE nha_cung_cap SET ten_ncc = ?, so_dien_thoai = ?, dia_chi = ?, email = ?, ghi_chu = ?, trang_thai = ? WHERE ma_ncc = ?",
                n.tenNCC(), n.soDienThoai(), n.diaChi(), n.email(), n.ghiChu(), n.trangThai(), n.maNCC());
    }

    // ---------- stock receipts ----------
    private static final String PN_SELECT = "SELECT pn.ma_pn, pn.ngay_nhap, pn.ma_nv, nv.ten_nv, pn.ma_ncc, ncc.ten_ncc, pn.trang_thai, pn.ghi_chu, "
            + "(SELECT COALESCE(SUM(so_luong * gia_nhap), 0) FROM chi_tiet_phieu_nhap WHERE ma_pn = pn.ma_pn) AS tong_tien, pn.ly_do_huy "
            + "FROM phieu_nhap pn JOIN nhan_vien nv ON nv.ma_nv = pn.ma_nv JOIN nha_cung_cap ncc ON ncc.ma_ncc = pn.ma_ncc ";

    public List<PhieuNhap> findPhieuNhap(Connection c, LocalDateTime from, LocalDateTime to) throws SQLException {
        return Jdbc.list(c, PN_SELECT + "WHERE pn.ngay_nhap >= ? AND pn.ngay_nhap < ? ORDER BY pn.ma_pn DESC",
                rs -> mapPN(rs, List.of()), from, to);
    }

    public PhieuNhap findPhieuNhapById(Connection c, int id) throws SQLException {
        List<ChiTietPhieuNhap> lines = Jdbc.list(c, "SELECT ct.ma_nl, nl.ten_nl, nl.don_vi_tinh, ct.so_luong, ct.gia_nhap FROM chi_tiet_phieu_nhap ct "
                        + "JOIN nguyen_lieu nl ON nl.ma_nl = ct.ma_nl WHERE ct.ma_pn = ? ORDER BY nl.ten_nl",
                rs -> new ChiTietPhieuNhap(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getBigDecimal(4), rs.getBigDecimal(5)), id);
        return Jdbc.one(c, PN_SELECT + "WHERE pn.ma_pn = ?", rs -> mapPN(rs, lines), id);
    }

    private static PhieuNhap mapPN(ResultSet rs, List<ChiTietPhieuNhap> lines) throws SQLException {
        return new PhieuNhap(rs.getInt("ma_pn"), Jdbc.dateTime(rs, "ngay_nhap"), rs.getInt("ma_nv"), rs.getString("ten_nv"),
                rs.getInt("ma_ncc"), rs.getString("ten_ncc"), TrangThaiPhieu.valueOf(rs.getString("trang_thai")),
                rs.getString("ghi_chu"), rs.getBigDecimal("tong_tien"), rs.getString("ly_do_huy"), lines);
    }

    public int insertPhieuNhap(Connection c, LocalDateTime now, int maNV, int maNCC, String ghiChu) throws SQLException {
        return Jdbc.insert(c, "INSERT INTO phieu_nhap (ngay_nhap, ma_nv, ma_ncc, trang_thai, ghi_chu) VALUES (?, ?, ?, 'DA_XAC_NHAN', ?)",
                now, maNV, maNCC, ghiChu);
    }

    public void insertChiTietPhieuNhap(Connection c, int maPN, int maNL, BigDecimal soLuong, BigDecimal giaNhap) throws SQLException {
        Jdbc.update(c, "INSERT INTO chi_tiet_phieu_nhap (ma_pn, ma_nl, so_luong, gia_nhap) VALUES (?, ?, ?, ?)", maPN, maNL, soLuong, giaNhap);
    }

    public int cancelPhieuNhap(Connection c, int maPN, String reason, int maNV, LocalDateTime now) throws SQLException {
        return Jdbc.update(c, "UPDATE phieu_nhap SET trang_thai = 'DA_HUY', ly_do_huy = ?, ma_nv_huy = ?, thoi_diem_huy = ? "
                + "WHERE ma_pn = ? AND trang_thai = 'DA_XAC_NHAN'", reason, maNV, now, maPN);
    }

    /** Most recent purchase price of an ingredient (default cost for issue vouchers). */
    public BigDecimal findLastPurchasePrice(Connection c, int maNL) throws SQLException {
        return Jdbc.one(c, "SELECT ct.gia_nhap FROM chi_tiet_phieu_nhap ct JOIN phieu_nhap pn ON pn.ma_pn = ct.ma_pn "
                + "WHERE ct.ma_nl = ? AND pn.trang_thai = 'DA_XAC_NHAN' ORDER BY pn.ngay_nhap DESC, pn.ma_pn DESC LIMIT 1", rs -> rs.getBigDecimal(1), maNL);
    }

    // ---------- stock issues ----------
    private static final String PX_SELECT = "SELECT px.ma_px, px.ngay_xuat, px.ma_nv, nv.ten_nv, px.ly_do, px.trang_thai, "
            + "(SELECT COALESCE(SUM(so_luong_xuat * don_gia_xuat), 0) FROM chi_tiet_phieu_xuat WHERE ma_px = px.ma_px) AS tong_tien, px.ly_do_huy "
            + "FROM phieu_xuat px JOIN nhan_vien nv ON nv.ma_nv = px.ma_nv ";

    public List<PhieuXuat> findPhieuXuat(Connection c, LocalDateTime from, LocalDateTime to) throws SQLException {
        return Jdbc.list(c, PX_SELECT + "WHERE px.ngay_xuat >= ? AND px.ngay_xuat < ? ORDER BY px.ma_px DESC",
                rs -> mapPX(rs, List.of()), from, to);
    }

    public PhieuXuat findPhieuXuatById(Connection c, int id) throws SQLException {
        List<ChiTietPhieuXuat> lines = Jdbc.list(c, "SELECT ct.ma_nl, nl.ten_nl, nl.don_vi_tinh, ct.so_luong_xuat, ct.don_gia_xuat FROM chi_tiet_phieu_xuat ct "
                        + "JOIN nguyen_lieu nl ON nl.ma_nl = ct.ma_nl WHERE ct.ma_px = ? ORDER BY nl.ten_nl",
                rs -> new ChiTietPhieuXuat(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getBigDecimal(4), rs.getBigDecimal(5)), id);
        return Jdbc.one(c, PX_SELECT + "WHERE px.ma_px = ?", rs -> mapPX(rs, lines), id);
    }

    private static PhieuXuat mapPX(ResultSet rs, List<ChiTietPhieuXuat> lines) throws SQLException {
        return new PhieuXuat(rs.getInt("ma_px"), Jdbc.dateTime(rs, "ngay_xuat"), rs.getInt("ma_nv"), rs.getString("ten_nv"),
                rs.getString("ly_do"), TrangThaiPhieu.valueOf(rs.getString("trang_thai")), rs.getBigDecimal("tong_tien"),
                rs.getString("ly_do_huy"), lines);
    }

    public int insertPhieuXuat(Connection c, LocalDateTime now, int maNV, String lyDo) throws SQLException {
        return Jdbc.insert(c, "INSERT INTO phieu_xuat (ngay_xuat, ma_nv, ly_do, trang_thai) VALUES (?, ?, ?, 'DA_XAC_NHAN')", now, maNV, lyDo);
    }

    public void insertChiTietPhieuXuat(Connection c, int maPX, int maNL, BigDecimal soLuong, BigDecimal donGia) throws SQLException {
        Jdbc.update(c, "INSERT INTO chi_tiet_phieu_xuat (ma_px, ma_nl, so_luong_xuat, don_gia_xuat) VALUES (?, ?, ?, ?)", maPX, maNL, soLuong, donGia);
    }

    public int cancelPhieuXuat(Connection c, int maPX, String reason, int maNV, LocalDateTime now) throws SQLException {
        return Jdbc.update(c, "UPDATE phieu_xuat SET trang_thai = 'DA_HUY', ly_do_huy = ?, ma_nv_huy = ?, thoi_diem_huy = ? "
                + "WHERE ma_px = ? AND trang_thai = 'DA_XAC_NHAN'", reason, maNV, now, maPX);
    }

    // ---------- stock movements ----------
    public void insertMovement(Connection c, int maNL, LocalDateTime now, LoaiBienDong loai, BigDecimal soLuong, BigDecimal tonSau,
                               String thamChieu, Integer maNV, String ghiChu) throws SQLException {
        Jdbc.insert(c, "INSERT INTO bien_dong_kho (ma_nl, thoi_diem, loai, so_luong, ton_sau, tham_chieu, ma_nv, ghi_chu) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                maNL, now, loai, soLuong, tonSau, thamChieu, maNV, ghiChu);
    }

    /** Movements in [from, to), optionally for one ingredient. */
    public List<BienDongKho> findMovements(Connection c, LocalDateTime from, LocalDateTime to, Integer maNL) throws SQLException {
        List<Object> params = new ArrayList<>(List.of(from, to));
        String where = "WHERE bd.thoi_diem >= ? AND bd.thoi_diem < ? ";
        if (maNL != null) {
            where += "AND bd.ma_nl = ? ";
            params.add(maNL);
        }
        return Jdbc.list(c, "SELECT bd.ma_bd, bd.ma_nl, nl.ten_nl, bd.thoi_diem, bd.loai, bd.so_luong, bd.ton_sau, bd.tham_chieu, nv.ten_nv, bd.ghi_chu "
                        + "FROM bien_dong_kho bd JOIN nguyen_lieu nl ON nl.ma_nl = bd.ma_nl LEFT JOIN nhan_vien nv ON nv.ma_nv = bd.ma_nv "
                        + where + "ORDER BY bd.thoi_diem DESC, bd.ma_bd DESC",
                rs -> new BienDongKho(rs.getInt(1), rs.getInt(2), rs.getString(3), Jdbc.dateTime(rs, "thoi_diem"),
                        LoaiBienDong.valueOf(rs.getString(5)), rs.getBigDecimal(6), rs.getBigDecimal(7), rs.getString(8),
                        rs.getString(9), rs.getString(10)), params.toArray());
    }

    /** Sum of all movements of an ingredient (must equal so_luong_ton). */
    public BigDecimal sumMovements(Connection c, int maNL) throws SQLException {
        return Jdbc.one(c, "SELECT COALESCE(SUM(so_luong), 0) FROM bien_dong_kho WHERE ma_nl = ?", rs -> rs.getBigDecimal(1), maNL);
    }
}
