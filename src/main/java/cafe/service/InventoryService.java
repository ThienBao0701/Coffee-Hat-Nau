package cafe.service;

import cafe.config.Database;
import cafe.dao.CatalogDao;
import cafe.dao.InventoryDao;
import cafe.model.BienDongKho;
import cafe.model.ChiTietHoaDon;
import cafe.model.ChiTietPhieuNhap;
import cafe.model.ChiTietPhieuXuat;
import cafe.model.CongThuc;
import cafe.model.LoaiBienDong;
import cafe.model.NguyenLieu;
import cafe.model.NhaCungCap;
import cafe.model.NhanVien;
import cafe.model.PhieuNhap;
import cafe.model.PhieuXuat;
import cafe.model.Size;
import cafe.util.BusinessException;
import cafe.util.Check;
import cafe.util.Money;
import cafe.util.Permission;
import cafe.util.Session;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Ingredients, recipes, suppliers, stock documents and stock movements.
 * Every stock change goes through {@link #applyMovement}, which locks the ingredient row, refuses
 * negative stock (unless the sale policy allows it) and writes a bien_dong_kho row, so
 * "current stock = opening stock + all recorded movements" always holds.
 */
public class InventoryService {

    private final Database db;
    private final AuditService audit;
    private final boolean allowNegativeOnSale;
    private final InventoryDao dao = new InventoryDao();
    private final CatalogDao catalogDao = new CatalogDao();

    public InventoryService(Database db, AuditService audit, boolean allowNegativeOnSale) {
        this.db = db;
        this.audit = audit;
        this.allowNegativeOnSale = allowNegativeOnSale;
    }

    private static BigDecimal qty(BigDecimal v) {
        return v.setScale(3, RoundingMode.HALF_UP);
    }

    /** The only code path that changes so_luong_ton. Returns the stock after the movement. */
    BigDecimal applyMovement(Connection c, int maNL, BigDecimal delta, LoaiBienDong loai, String thamChieu, Integer maNV,
                             String ghiChu, boolean allowNegative) throws SQLException {
        BigDecimal current = dao.lockStock(c, maNL);
        if (current == null) {
            throw new BusinessException("Nguyên liệu không tồn tại.");
        }
        BigDecimal after = qty(current.add(delta));
        if (after.signum() < 0 && !allowNegative) {
            NguyenLieu nl = dao.findNguyenLieuById(c, maNL);
            throw new BusinessException("Không đủ tồn kho \"" + nl.tenNL() + "\": cần " + qty(delta.negate()).stripTrailingZeros().toPlainString()
                    + " " + nl.donViTinh() + ", hiện còn " + current.stripTrailingZeros().toPlainString() + " " + nl.donViTinh() + ".");
        }
        dao.setStock(c, maNL, after);
        dao.insertMovement(c, maNL, LocalDateTime.now(), loai, qty(delta), after, thamChieu, maNV, ghiChu);
        return after;
    }

    // ---------- ingredients ----------
    public List<NguyenLieu> findNguyenLieu() {
        Session.require(Permission.MANAGE_INVENTORY);
        return db.read(c -> dao.findNguyenLieu(c, false));
    }

    public List<NguyenLieu> findLowStock() {
        Session.require(Permission.MANAGE_INVENTORY);
        return db.read(dao::findLowStock);
    }

    /** Creates (with optional opening stock, recorded as TON_DAU) or edits an ingredient. Stock itself is not editable here. */
    public void saveNguyenLieu(NguyenLieu input, BigDecimal tonDau) {
        NhanVien me = Session.require(Permission.MANAGE_INVENTORY);
        String ten = Check.required(input.tenNL(), "tên nguyên liệu", 100);
        String dvt = Check.required(input.donViTinh(), "đơn vị tính", 20);
        BigDecimal canhBao = Check.nonNegative(input.mucCanhBao(), "Mức cảnh báo");
        BigDecimal opening = tonDau == null ? BigDecimal.ZERO : Check.nonNegative(tonDau, "Tồn đầu");
        NguyenLieu n = new NguyenLieu(input.maNL(), ten, dvt, BigDecimal.ZERO, qty(canhBao), input.trangThai());
        db.tx(c -> {
            if (dao.nguyenLieuNameExists(c, ten, n.maNL())) {
                throw new BusinessException("Nguyên liệu \"" + ten + "\" đã tồn tại.");
            }
            if (n.maNL() == null) {
                int id = dao.insertNguyenLieu(c, n);
                if (opening.signum() > 0) {
                    applyMovement(c, id, opening, LoaiBienDong.TON_DAU, null, me.maNV(), "Tồn đầu khi tạo nguyên liệu", false);
                }
                audit.log(c, "THEM", "NGUYEN_LIEU", id, ten);
            } else {
                dao.updateNguyenLieu(c, n);
                audit.log(c, "SUA", "NGUYEN_LIEU", n.maNL(), ten);
            }
            return null;
        });
    }

    /** Stock-take correction: sets the counted quantity and records the difference (manager only). */
    public void dieuChinhTon(int maNL, BigDecimal soLuongThucTe, String lyDo) {
        NhanVien me = Session.require(Permission.ADJUST_STOCK);
        BigDecimal actual = qty(Check.nonNegative(soLuongThucTe, "Số lượng thực tế"));
        String reason = Check.required(lyDo, "lý do điều chỉnh", 255);
        db.tx(c -> {
            BigDecimal current = dao.lockStock(c, maNL);
            if (current == null) {
                throw new BusinessException("Nguyên liệu không tồn tại.");
            }
            BigDecimal delta = actual.subtract(current);
            if (delta.signum() == 0) {
                throw new BusinessException("Số lượng thực tế bằng tồn kho hiện tại, không cần điều chỉnh.");
            }
            applyMovement(c, maNL, delta, LoaiBienDong.DIEU_CHINH, null, me.maNV(), reason, false);
            audit.log(c, "DIEU_CHINH_KHO", "NGUYEN_LIEU", maNL, current.stripTrailingZeros().toPlainString() + " -> "
                    + actual.stripTrailingZeros().toPlainString() + ": " + reason);
            return null;
        });
    }

    // ---------- recipes ----------
    public List<CongThuc> findCongThuc(int maMon) {
        Session.require(Permission.MANAGE_INVENTORY);
        return db.read(c -> dao.findCongThuc(c, maMon));
    }

    public void saveCongThuc(Integer maCT, int maMon, int maNL, BigDecimal dinhLuong) {
        Session.require(Permission.MANAGE_INVENTORY);
        BigDecimal dl = qty(Check.positive(dinhLuong, "Định lượng"));
        db.tx(c -> {
            if (dao.congThucExists(c, maMon, maNL, maCT)) {
                throw new BusinessException("Nguyên liệu này đã có trong công thức của món.");
            }
            if (maCT == null) {
                audit.log(c, "THEM", "CONG_THUC", dao.insertCongThuc(c, maMon, maNL, dl), "Món " + maMon + ", NL " + maNL + ": " + dl);
            } else {
                dao.updateCongThuc(c, maCT, maNL, dl);
                audit.log(c, "SUA", "CONG_THUC", maCT, "NL " + maNL + ": " + dl);
            }
            return null;
        });
    }

    public void deleteCongThuc(int maCT) {
        Session.require(Permission.MANAGE_INVENTORY);
        db.tx(c -> {
            dao.deleteCongThuc(c, maCT);
            audit.log(c, "XOA", "CONG_THUC", maCT, null);
            return null;
        });
    }

    // ---------- suppliers ----------
    public List<NhaCungCap> findNhaCungCap(boolean activeOnly) {
        Session.require(Permission.MANAGE_INVENTORY);
        return db.read(c -> dao.findNhaCungCap(c, activeOnly));
    }

    public void saveNhaCungCap(NhaCungCap input) {
        Session.require(Permission.MANAGE_INVENTORY);
        NhaCungCap n = new NhaCungCap(input.maNCC(), Check.required(input.tenNCC(), "tên nhà cung cấp", 150),
                Check.optional(input.soDienThoai(), "Số điện thoại", 15), Check.optional(input.diaChi(), "Địa chỉ", 255),
                Check.email(input.email()), Check.optional(input.ghiChu(), "Ghi chú", 255), input.trangThai());
        db.tx(c -> {
            if (dao.nhaCungCapNameExists(c, n.tenNCC(), n.maNCC())) {
                throw new BusinessException("Nhà cung cấp \"" + n.tenNCC() + "\" đã tồn tại.");
            }
            if (n.maNCC() == null) {
                audit.log(c, "THEM", "NHA_CUNG_CAP", dao.insertNhaCungCap(c, n), n.tenNCC());
            } else {
                dao.updateNhaCungCap(c, n);
                audit.log(c, "SUA", "NHA_CUNG_CAP", n.maNCC(), n.tenNCC());
            }
            return null;
        });
    }

    // ---------- stock receipts ----------
    public List<PhieuNhap> findPhieuNhap(LocalDate from, LocalDate to) {
        Session.require(Permission.MANAGE_INVENTORY);
        return db.read(c -> dao.findPhieuNhap(c, from.atStartOfDay(), to.plusDays(1).atStartOfDay()));
    }

    public PhieuNhap findPhieuNhap(int maPN) {
        Session.require(Permission.MANAGE_INVENTORY);
        return db.read(c -> dao.findPhieuNhapById(c, maPN));
    }

    public BigDecimal findLastPurchasePrice(int maNL) {
        Session.require(Permission.MANAGE_INVENTORY);
        BigDecimal p = db.read(c -> dao.findLastPurchasePrice(c, maNL));
        return p == null ? BigDecimal.ZERO : p;
    }

    /**
     * taoPhieuNhap + themNguyenLieu + capNhatTonKho in ONE transaction:
     * header, lines and the stock increase are saved together or not at all.
     */
    public int taoPhieuNhap(int maNCC, String ghiChu, List<ChiTietPhieuNhap> lines) {
        NhanVien me = Session.require(Permission.MANAGE_INVENTORY);
        validateLines(lines.stream().map(ChiTietPhieuNhap::maNL).toList());
        for (ChiTietPhieuNhap l : lines) {
            Check.positive(l.soLuong(), "Số lượng nhập");
            Check.nonNegative(l.giaNhap(), "Giá nhập");
        }
        String note = Check.optional(ghiChu, "Ghi chú", 255);
        return db.tx(c -> {
            boolean supplierOk = dao.findNhaCungCap(c, true).stream().anyMatch(n -> n.maNCC() == maNCC);
            if (!supplierOk) {
                throw new BusinessException("Nhà cung cấp không tồn tại hoặc đã ngừng giao dịch.");
            }
            int maPN = dao.insertPhieuNhap(c, LocalDateTime.now(), me.maNV(), maNCC, note);
            for (ChiTietPhieuNhap l : byIngredient(lines, ChiTietPhieuNhap::maNL)) {
                dao.insertChiTietPhieuNhap(c, maPN, l.maNL(), qty(l.soLuong()), Money.round(l.giaNhap()));
                applyMovement(c, l.maNL(), l.soLuong(), LoaiBienDong.NHAP, "PN" + maPN, me.maNV(), null, false);
            }
            audit.log(c, "NHAP_KHO", "PHIEU_NHAP", maPN, lines.size() + " nguyên liệu");
            return maPN;
        });
    }

    /** Cancels a confirmed receipt by recording reversing movements (fails if the stock was already used). */
    public void huyPhieuNhap(int maPN, String lyDo) {
        NhanVien me = Session.require(Permission.ADJUST_STOCK);
        String reason = Check.required(lyDo, "lý do hủy", 255);
        db.tx(c -> {
            PhieuNhap pn = dao.findPhieuNhapById(c, maPN);
            if (pn == null || dao.cancelPhieuNhap(c, maPN, reason, me.maNV(), LocalDateTime.now()) != 1) {
                throw new BusinessException("Phiếu nhập không tồn tại hoặc đã bị hủy.");
            }
            for (ChiTietPhieuNhap l : byIngredient(pn.chiTiet(), ChiTietPhieuNhap::maNL)) {
                applyMovement(c, l.maNL(), l.soLuong().negate(), LoaiBienDong.HUY_NHAP, "PN" + maPN, me.maNV(), reason, false);
            }
            audit.log(c, "HUY_PHIEU_NHAP", "PHIEU_NHAP", maPN, reason);
            return null;
        });
    }

    // ---------- stock issues ----------
    public List<PhieuXuat> findPhieuXuat(LocalDate from, LocalDate to) {
        Session.require(Permission.MANAGE_INVENTORY);
        return db.read(c -> dao.findPhieuXuat(c, from.atStartOfDay(), to.plusDays(1).atStartOfDay()));
    }

    public PhieuXuat findPhieuXuat(int maPX) {
        Session.require(Permission.MANAGE_INVENTORY);
        return db.read(c -> dao.findPhieuXuatById(c, maPX));
    }

    /** taoPhieuXuat: header, lines and stock decrease in one transaction. Stock may not go negative. */
    public int taoPhieuXuat(String lyDo, List<ChiTietPhieuXuat> lines) {
        NhanVien me = Session.require(Permission.MANAGE_INVENTORY);
        String reason = Check.required(lyDo, "lý do xuất", 255);
        validateLines(lines.stream().map(ChiTietPhieuXuat::maNL).toList());
        for (ChiTietPhieuXuat l : lines) {
            Check.positive(l.soLuongXuat(), "Số lượng xuất");
            Check.nonNegative(l.donGiaXuat(), "Đơn giá xuất");
        }
        return db.tx(c -> {
            int maPX = dao.insertPhieuXuat(c, LocalDateTime.now(), me.maNV(), reason);
            for (ChiTietPhieuXuat l : byIngredient(lines, ChiTietPhieuXuat::maNL)) {
                dao.insertChiTietPhieuXuat(c, maPX, l.maNL(), qty(l.soLuongXuat()), Money.round(l.donGiaXuat()));
                applyMovement(c, l.maNL(), l.soLuongXuat().negate(), LoaiBienDong.XUAT, "PX" + maPX, me.maNV(), reason, false);
            }
            audit.log(c, "XUAT_KHO", "PHIEU_XUAT", maPX, reason);
            return maPX;
        });
    }

    public void huyPhieuXuat(int maPX, String lyDo) {
        NhanVien me = Session.require(Permission.ADJUST_STOCK);
        String reason = Check.required(lyDo, "lý do hủy", 255);
        db.tx(c -> {
            PhieuXuat px = dao.findPhieuXuatById(c, maPX);
            if (px == null || dao.cancelPhieuXuat(c, maPX, reason, me.maNV(), LocalDateTime.now()) != 1) {
                throw new BusinessException("Phiếu xuất không tồn tại hoặc đã bị hủy.");
            }
            for (ChiTietPhieuXuat l : byIngredient(px.chiTiet(), ChiTietPhieuXuat::maNL)) {
                applyMovement(c, l.maNL(), l.soLuongXuat(), LoaiBienDong.HUY_XUAT, "PX" + maPX, me.maNV(), reason, false);
            }
            audit.log(c, "HUY_PHIEU_XUAT", "PHIEU_XUAT", maPX, reason);
            return null;
        });
    }

    /** Ingredient rows are always locked in id order (as in consumeForSale) so concurrent documents cannot deadlock. */
    private static <T> List<T> byIngredient(List<T> lines, java.util.function.ToIntFunction<T> id) {
        return lines.stream().sorted(java.util.Comparator.comparingInt(id)).toList();
    }

    private static void validateLines(List<Integer> ingredientIds) {
        if (ingredientIds.isEmpty()) {
            throw new BusinessException("Phiếu phải có ít nhất một nguyên liệu.");
        }
        Set<Integer> seen = new HashSet<>();
        for (Integer id : ingredientIds) {
            if (!seen.add(id)) {
                throw new BusinessException("Mỗi nguyên liệu chỉ được xuất hiện một lần trong phiếu.");
            }
        }
    }

    // ---------- movements ----------
    public List<BienDongKho> findMovements(LocalDate from, LocalDate to, Integer maNL) {
        Session.require(Permission.MANAGE_INVENTORY);
        return db.read(c -> dao.findMovements(c, from.atStartOfDay(), to.plusDays(1).atStartOfDay(), maNL));
    }

    // ---------- consumption from sales ----------
    /**
     * Deducts the ingredients used by a paid invoice (called inside the payment transaction, so it
     * happens exactly once: only the OPEN -> PAID transition triggers it).
     * Quantity per line = recipe quantity x size multiplier x number of drinks.
     */
    void consumeForSale(Connection c, int maHD, List<ChiTietHoaDon> lines, int maNV) throws SQLException {
        Map<Integer, BigDecimal> needed = new TreeMap<>(); // sorted by id => consistent lock order
        Map<Integer, List<CongThuc>> recipes = new HashMap<>();
        Map<Integer, Size> sizes = new HashMap<>();
        for (ChiTietHoaDon line : lines) {
            List<CongThuc> recipe = recipes.get(line.maMon());
            if (recipe == null) {
                recipe = dao.findCongThuc(c, line.maMon());
                recipes.put(line.maMon(), recipe);
            }
            Size size = sizes.get(line.maSize());
            if (size == null) {
                size = catalogDao.findSizeById(c, line.maSize());
                sizes.put(line.maSize(), size);
            }
            for (CongThuc ct : recipe) {
                BigDecimal q = ct.dinhLuong().multiply(size.heSoDinhLuong()).multiply(BigDecimal.valueOf(line.soLuong()));
                needed.merge(ct.maNL(), q, BigDecimal::add);
            }
        }
        for (Map.Entry<Integer, BigDecimal> e : needed.entrySet()) {
            applyMovement(c, e.getKey(), e.getValue().negate(), LoaiBienDong.BAN_HANG, "HD" + maHD, maNV, null, allowNegativeOnSale);
        }
    }
}
