package cafe.service;

import cafe.config.Database;
import cafe.dao.CatalogDao;
import cafe.dao.CustomerDao;
import cafe.dao.InvoiceDao;
import cafe.dao.PriceDao;
import cafe.dao.PromotionDao;
import cafe.dao.TableDao;
import cafe.model.Ban;
import cafe.model.ChiTietHoaDon;
import cafe.model.DonDatHang;
import cafe.model.HoaDon;
import cafe.model.InvoiceDetail;
import cafe.model.KhuyenMai;
import cafe.model.LoaiDon;
import cafe.model.Mon;
import cafe.model.NhanVien;
import cafe.model.PhuongThucThanhToan;
import cafe.model.Size;
import cafe.model.Thue;
import cafe.model.Topping;
import cafe.model.TrangThaiDatBan;
import cafe.model.TrangThaiHoaDon;
import cafe.model.ThanhToan;
import cafe.util.AccessDeniedException;
import cafe.util.BusinessException;
import cafe.util.Check;
import cafe.util.Money;
import cafe.util.Permission;
import cafe.util.Session;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Order and invoice lifecycle:  OPEN --pay--> PAID --refund--> REFUNDED
 *                                OPEN --cancel/merge--> CANCELLED
 * Every state change uses "UPDATE ... WHERE trang_thai = expected" inside a transaction that first
 * locks the invoice row, so repeated clicks or two terminals cannot pay/cancel the same invoice twice.
 */
public class OrderService {

    public static final int MAX_QTY = 99;
    public static final int MAX_TOPPING_QTY = 5;

    /** Payment request built by the payment dialog. tienKhachDua is only used for cash. */
    public record PaymentRequest(int maHD, Integer maKM, Integer maKH, PhuongThucThanhToan phuongThuc, BigDecimal tienKhachDua) {
    }

    private final Database db;
    private final AuditService audit;
    private final InventoryService inventory;
    private final InvoiceDao dao = new InvoiceDao();
    private final TableDao tableDao = new TableDao();
    private final CatalogDao catalogDao = new CatalogDao();
    private final PriceDao priceDao = new PriceDao();
    private final PromotionDao promotionDao = new PromotionDao();
    private final CustomerDao customerDao = new CustomerDao();

    public OrderService(Database db, AuditService audit, InventoryService inventory) {
        this.db = db;
        this.audit = audit;
        this.inventory = inventory;
    }

    // ---------- opening orders ----------
    /** Opens a dine-in order. The table row is locked so two terminals cannot open it at the same time. */
    public int openTableOrder(int maBan) {
        NhanVien me = Session.require(Permission.SELL);
        return db.tx(c -> openTableOrder(c, maBan, null, me));
    }

    private int openTableOrder(Connection c, int maBan, Integer maKH, NhanVien me) throws SQLException {
        tableDao.lockBan(c, maBan);
        Ban ban = tableDao.findBanById(c, maBan);
        if (ban == null || !ban.trangThai()) {
            throw new BusinessException("Bàn không tồn tại hoặc đang ngừng phục vụ.");
        }
        Integer existing = dao.findOpenIdByTable(c, maBan);
        if (existing != null) {
            throw new BusinessException(ban.tenBan() + " đang có hóa đơn HĐ " + existing + ".");
        }
        int id = dao.insert(c, LoaiDon.TAI_BAN, maBan, maKH, me.maNV(), null, LocalDateTime.now());
        audit.log(c, "MO_BAN", "HOA_DON", id, ban.tenBan());
        return id;
    }

    /** Takeaway or delivery order (no table). Delivery requires an address. */
    public int openOrder(LoaiDon loai, String diaChiGiao) {
        NhanVien me = Session.require(Permission.SELL);
        if (loai == LoaiDon.TAI_BAN) {
            throw new BusinessException("Đơn tại bàn phải được mở từ sơ đồ bàn.");
        }
        String address = loai == LoaiDon.GIAO_HANG ? Check.required(diaChiGiao, "địa chỉ giao hàng", 255) : null;
        return db.tx(c -> {
            int id = dao.insert(c, loai, null, null, me.maNV(), address, LocalDateTime.now());
            audit.log(c, "TAO_DON", "HOA_DON", id, loai.toString());
            return id;
        });
    }

    /** Guest with a reservation arrives: reservation -> HOAN_THANH and an order is opened on its table. */
    public int checkInReservation(int maDDH) {
        NhanVien me = Session.require(Permission.SELL);
        Session.require(Permission.MANAGE_RESERVATIONS);
        return db.tx(c -> {
            DonDatHang d = customerDao.findReservation(c, maDDH);
            if (d == null || customerDao.updateReservationStatus(c, maDDH, TrangThaiDatBan.HOAN_THANH,
                    TrangThaiDatBan.CHO_XAC_NHAN, TrangThaiDatBan.DA_XAC_NHAN) != 1) {
                throw new BusinessException("Đơn đặt bàn không ở trạng thái chờ khách đến.");
            }
            if (!d.thoiDiemDat().toLocalDate().equals(LocalDate.now())) {
                throw new BusinessException("Chỉ nhận bàn cho đơn đặt trong hôm nay.");
            }
            int id = openTableOrder(c, d.maBan(), d.maKH(), me);
            audit.log(c, "NHAN_BAN", "DON_DAT_HANG", maDDH, "Mở HĐ " + id);
            return id;
        });
    }

    // ---------- reading ----------
    /**
     * Any staff member can work on OPEN orders (shared floor). A closed invoice is visible to the
     * employee who created it or collected its payment; managers see all.
     */
    public InvoiceDetail getInvoice(int maHD) {
        NhanVien me = Session.require(Permission.VIEW_OWN_INVOICES);
        InvoiceDetail d = loadDetail(maHD);
        boolean mine = d.hoaDon().maNV() == me.maNV() || (d.thanhToan() != null && d.thanhToan().maNV() == me.maNV());
        if (d.hoaDon().trangThai() != TrangThaiHoaDon.OPEN && !mine && !Session.can(Permission.VIEW_ALL_INVOICES)) {
            throw new AccessDeniedException("Bạn chỉ được xem hóa đơn do mình lập hoặc thu tiền.");
        }
        return d;
    }

    private InvoiceDetail loadDetail(int maHD) {
        InvoiceDetail d = db.read(c -> {
            HoaDon hd = dao.findById(c, maHD);
            return hd == null ? null : new InvoiceDetail(hd, dao.findLines(c, maHD), dao.findPayment(c, maHD));
        });
        if (d == null) {
            throw new BusinessException("Hóa đơn không tồn tại.");
        }
        return d;
    }

    /** Open orders without a table (takeaway / delivery). */
    public List<HoaDon> findOpenNonTableOrders() {
        Session.require(Permission.SELL);
        return db.read(dao::findOpen).stream().filter(h -> h.maBan() == null).toList();
    }

    public List<HoaDon> findOpenOrders() {
        Session.require(Permission.SELL);
        return db.read(dao::findOpen);
    }

    /** Invoice history; employees only see invoices they created. */
    public List<HoaDon> search(LocalDate from, LocalDate to, TrangThaiHoaDon status, Integer maHD) {
        NhanVien me = Session.require(Permission.VIEW_OWN_INVOICES);
        Check.notNull(from, "từ ngày");
        Check.notNull(to, "đến ngày");
        Check.dateRange(from, to, "khoảng thời gian");
        Integer onlyMine = Session.can(Permission.VIEW_ALL_INVOICES) ? null : me.maNV();
        return db.read(c -> dao.search(c, from.atStartOfDay(), to.plusDays(1).atStartOfDay(), status, onlyMine, maHD));
    }

    // ---------- editing lines ----------
    private InvoiceDao.LockedInvoice requireOpen(Connection c, int maHD) throws SQLException {
        InvoiceDao.LockedInvoice hd = dao.lock(c, maHD);
        if (hd == null) {
            throw new BusinessException("Hóa đơn không tồn tại.");
        }
        if (hd.trangThai() != TrangThaiHoaDon.OPEN) {
            throw new BusinessException("Hóa đơn HĐ " + maHD + " đang ở trạng thái \"" + hd.trangThai() + "\", không thể thay đổi.");
        }
        return hd;
    }

    /**
     * Adds a product line. The unit price is read from the price list in effect now (never trusted
     * from the UI) and stored on the line as a snapshot, together with the product, size and topping names.
     * @param toppings topping id -> quantity per drink
     */
    public int addItem(int maHD, int maMon, int maSize, int soLuong, Map<Integer, Integer> toppings, String ghiChu) {
        Session.require(Permission.SELL);
        validateQty(soLuong, toppings);
        String note = Check.optional(ghiChu, "Ghi chú", 255);
        return db.tx(c -> {
            requireOpen(c, maHD);
            Mon mon = catalogDao.findMonById(c, maMon);
            if (mon == null || !mon.trangThai()) {
                throw new BusinessException("Món này đã ngừng bán.");
            }
            Size size = catalogDao.findSizeById(c, maSize);
            BigDecimal price = size == null ? null : priceDao.findPrice(c, maMon, maSize, LocalDate.now());
            if (price == null) {
                throw new BusinessException("Món \"" + mon.tenMon() + "\" chưa có giá cho size này trong bảng giá hiện hành.");
            }
            int maCTHD = dao.insertLine(c, maHD, maMon, maSize, mon.tenMon(), size.tenSize(), soLuong, price, note);
            insertToppings(c, maCTHD, toppings);
            return maCTHD;
        });
    }

    /** Changes quantity, note and toppings of a line on an OPEN order (toppings are re-priced at current prices). */
    public void updateLine(int maCTHD, int soLuong, Map<Integer, Integer> toppings, String ghiChu) {
        Session.require(Permission.SELL);
        validateQty(soLuong, toppings);
        String note = Check.optional(ghiChu, "Ghi chú", 255);
        db.tx(c -> {
            Integer maHD = dao.findInvoiceIdOfLine(c, maCTHD);
            if (maHD == null) {
                throw new BusinessException("Dòng hóa đơn không tồn tại.");
            }
            requireOpen(c, maHD);
            dao.updateLine(c, maCTHD, soLuong, note);
            dao.deleteLineToppings(c, maCTHD);
            insertToppings(c, maCTHD, toppings);
            return null;
        });
    }

    /** capNhatSoLuong: quick +/- from the order screen. Quantity 0 removes the line. */
    public void capNhatSoLuong(int maCTHD, int soLuongMoi) {
        Session.require(Permission.SELL);
        if (soLuongMoi > MAX_QTY) {
            throw new BusinessException("Số lượng tối đa mỗi dòng là " + MAX_QTY + ".");
        }
        db.tx(c -> {
            Integer maHD = dao.findInvoiceIdOfLine(c, maCTHD);
            if (maHD == null) {
                throw new BusinessException("Dòng hóa đơn không tồn tại.");
            }
            requireOpen(c, maHD);
            ChiTietHoaDon line = dao.findLines(c, maHD).stream().filter(l -> l.maCTHD() == maCTHD).findFirst().orElseThrow();
            if (soLuongMoi <= 0) {
                dao.deleteLine(c, maCTHD);
            } else {
                dao.updateLine(c, maCTHD, soLuongMoi, line.ghiChu());
            }
            return null;
        });
    }

    public void removeLine(int maCTHD) {
        capNhatSoLuong(maCTHD, 0);
    }

    private void insertToppings(Connection c, int maCTHD, Map<Integer, Integer> toppings) throws SQLException {
        if (toppings == null) {
            return;
        }
        for (Map.Entry<Integer, Integer> e : toppings.entrySet()) {
            Topping t = catalogDao.findToppingById(c, e.getKey());
            if (t == null || !t.trangThai()) {
                throw new BusinessException("Topping đã ngừng phục vụ.");
            }
            dao.insertLineTopping(c, maCTHD, t.maTopping(), t.tenTopping(), e.getValue(), t.giaThem());
        }
    }

    private static void validateQty(int soLuong, Map<Integer, Integer> toppings) {
        if (soLuong < 1 || soLuong > MAX_QTY) {
            throw new BusinessException("Số lượng phải từ 1 đến " + MAX_QTY + ".");
        }
        if (toppings != null) {
            for (int q : toppings.values()) {
                if (q < 1 || q > MAX_TOPPING_QTY) {
                    throw new BusinessException("Số lượng mỗi topping phải từ 1 đến " + MAX_TOPPING_QTY + ".");
                }
            }
        }
    }

    // ---------- tables ----------
    public void transferTable(int maHD, int maBanMoi) {
        Session.require(Permission.SELL);
        db.tx(c -> {
            InvoiceDao.LockedInvoice hd = requireOpen(c, maHD);
            if (hd.loaiDon() != LoaiDon.TAI_BAN) {
                throw new BusinessException("Chỉ chuyển bàn cho đơn tại bàn.");
            }
            if (hd.maBan() != null && hd.maBan() == maBanMoi) {
                throw new BusinessException("Hóa đơn đã ở bàn này.");
            }
            tableDao.lockBan(c, maBanMoi);
            Ban to = tableDao.findBanById(c, maBanMoi);
            if (to == null || !to.trangThai()) {
                throw new BusinessException("Bàn mới không tồn tại hoặc đang ngừng phục vụ.");
            }
            if (dao.findOpenIdByTable(c, maBanMoi) != null) {
                throw new BusinessException(to.tenBan() + " đang có khách. Hãy dùng chức năng gộp đơn.");
            }
            dao.updateTable(c, maHD, maBanMoi);
            audit.log(c, "CHUYEN_BAN", "HOA_DON", maHD, "Bàn " + hd.maBan() + " -> " + to.tenBan());
            return null;
        });
    }

    /** Moves all lines of 'fromHD' into 'intoHD' and closes 'fromHD' as CANCELLED (reason recorded). */
    public void mergeOrders(int fromHD, int intoHD) {
        NhanVien me = Session.require(Permission.SELL);
        if (fromHD == intoHD) {
            throw new BusinessException("Không thể gộp hóa đơn với chính nó.");
        }
        db.tx(c -> {
            // lock in id order so two concurrent merges cannot deadlock
            requireOpen(c, Math.min(fromHD, intoHD));
            requireOpen(c, Math.max(fromHD, intoHD));
            dao.moveLines(c, fromHD, intoHD);
            dao.close(c, fromHD, TrangThaiHoaDon.OPEN, TrangThaiHoaDon.CANCELLED, "Gộp vào HĐ " + intoHD, me.maNV(), LocalDateTime.now());
            audit.log(c, "GOP_DON", "HOA_DON", intoHD, "Gộp HĐ " + fromHD + " vào HĐ " + intoHD);
            return null;
        });
    }

    /** Cancels an OPEN order (nothing was charged or taken from stock yet). A reason is required. */
    public void cancelOrder(int maHD, String lyDo) {
        NhanVien me = Session.require(Permission.SELL);
        String reason = Check.required(lyDo, "lý do hủy", 255);
        db.tx(c -> {
            requireOpen(c, maHD);
            dao.close(c, maHD, TrangThaiHoaDon.OPEN, TrangThaiHoaDon.CANCELLED, reason, me.maNV(), LocalDateTime.now());
            audit.log(c, "HUY_HOA_DON", "HOA_DON", maHD, reason);
            return null;
        });
    }

    public void updateNote(int maHD, String ghiChu) {
        Session.require(Permission.SELL);
        String note = Check.optional(ghiChu, "Ghi chú", 255);
        db.tx(c -> {
            requireOpen(c, maHD);
            dao.updateNote(c, maHD, note);
            return null;
        });
    }

    // ---------- payment ----------
    /** Totals for the payment dialog, computed with the same rules as {@link #pay}. */
    public PriceCalculator.Totals preview(int maHD, Integer maKM) {
        Session.require(Permission.SELL);
        LocalDate today = LocalDate.now();
        return db.read(c -> {
            BigDecimal subtotal = subtotal(dao.findLines(c, maHD));
            KhuyenMai km = maKM == null ? null : promotionDao.findKhuyenMaiById(c, maKM);
            PriceCalculator.checkEligible(km, subtotal, today);
            Thue thue = promotionDao.findThueOn(c, today);
            return PriceCalculator.calculate(subtotal, km, thue == null ? null : thue.phanTram());
        });
    }

    private static BigDecimal subtotal(List<ChiTietHoaDon> lines) {
        return lines.stream().map(ChiTietHoaDon::tinhThanhTien).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Pays an OPEN invoice in one transaction: recompute totals, consume stock, mark PAID with
     * snapshots and insert the payment row. If anything fails nothing is saved.
     * A second call for the same invoice fails (status is no longer OPEN, and thanh_toan.ma_hd is UNIQUE).
     */
    public InvoiceDetail pay(PaymentRequest req) {
        NhanVien me = Session.require(Permission.SELL);
        Check.notNull(req.phuongThuc(), "phương thức thanh toán");
        int[] cashShift = new int[1];
        LocalDate today = LocalDate.now();
        db.tx(c -> {
            cashShift[0] = CashShiftService.lockOpenShiftForPayment(c, me.maNV());
            requireOpen(c, req.maHD());
            List<ChiTietHoaDon> lines = dao.findLines(c, req.maHD());
            if (lines.isEmpty()) {
                throw new BusinessException("Hóa đơn chưa có món nào.");
            }
            BigDecimal subtotal = subtotal(lines);
            KhuyenMai km = null;
            if (req.maKM() != null) {
                km = promotionDao.findKhuyenMaiById(c, req.maKM());
                if (km == null) {
                    throw new BusinessException("Khuyến mãi không tồn tại.");
                }
                PriceCalculator.checkEligible(km, subtotal, today);
            }
            Thue thue = promotionDao.findThueOn(c, today);
            PriceCalculator.Totals t = PriceCalculator.calculate(subtotal, km, thue == null ? null : thue.phanTram());

            BigDecimal paid = t.tongTien();
            if (req.phuongThuc() == PhuongThucThanhToan.TIEN_MAT) {
                paid = Check.notNull(req.tienKhachDua(), "số tiền khách đưa");
                if (paid.compareTo(t.tongTien()) < 0) {
                    throw new BusinessException("Số tiền khách đưa (" + Money.format(paid) + ") nhỏ hơn số tiền phải trả ("
                            + Money.format(t.tongTien()) + ").");
                }
            }
            if (req.maKH() != null && customerDao.findById(c, req.maKH()) == null) {
                throw new BusinessException("Khách hàng không tồn tại.");
            }

            inventory.consumeForSale(c, req.maHD(), lines, me.maNV());
            int changed = dao.markPaid(c, req.maHD(), req.maKH(), km == null ? null : km.maKM(), km == null ? null : km.tenKM(),
                    thue == null ? null : thue.maThue(), thue == null ? BigDecimal.ZERO : thue.phanTram(),
                    t.tamTinh(), t.tienGiam(), t.tienThue(), t.tongTien());
            if (changed != 1) {
                throw new BusinessException("Hóa đơn đã được thanh toán.");
            }
            dao.insertPayment(c, req.maHD(), req.phuongThuc(), t.tongTien(), paid, paid.subtract(t.tongTien()),
                    LocalDateTime.now(), me.maNV(), cashShift[0]);
            audit.log(c, "THANH_TOAN", "HOA_DON", req.maHD(), req.phuongThuc() + " " + Money.format(t.tongTien()));
            return null;
        });
        // the cashier may not be the creator; the payment is already committed, so never fail here on visibility
        return loadDetail(req.maHD());
    }

    /**
     * Full refund of a PAID invoice (manager only, reason required). Ingredients are NOT returned to
     * stock because the drinks were already prepared; use a stock adjustment if something was reusable.
     */
    public void refund(int maHD, String lyDo) {
        Session.require(Permission.REFUND_INVOICE);
        ThanhToan payment = db.read(c -> dao.findPayment(c, maHD));
        if (payment == null) throw new BusinessException("Không tìm thấy khoản thanh toán gốc.");
        refund(maHD, payment.phuongThuc(), lyDo);
    }

    public void refund(int maHD, PhuongThucThanhToan refundMethod, String lyDo) {
        NhanVien me = Session.require(Permission.REFUND_INVOICE);
        Check.notNull(refundMethod, "phương thức hoàn tiền");
        String reason = Check.required(lyDo, "lý do hoàn tiền", 255);
        db.tx(c -> {
            Integer cashShift = null;
            if (refundMethod == PhuongThucThanhToan.TIEN_MAT) {
                cashShift = CashShiftService.lockOpenShiftForPayment(c, me.maNV());
            }
            InvoiceDao.LockedInvoice hd = dao.lock(c, maHD);
            if (hd == null || hd.trangThai() != TrangThaiHoaDon.PAID) {
                throw new BusinessException("Chỉ hoàn tiền được hóa đơn đã thanh toán.");
            }
            ThanhToan payment = dao.findPayment(c, maHD);
            if (payment == null) throw new BusinessException("Không tìm thấy khoản thanh toán gốc.");
            dao.close(c, maHD, TrangThaiHoaDon.PAID, TrangThaiHoaDon.REFUNDED, reason, me.maNV(), LocalDateTime.now());
            dao.insertRefund(c, maHD, cashShift, me.maNV(), refundMethod, payment.soTien(), LocalDateTime.now(), reason);
            audit.log(c, "HOAN_TIEN", "HOA_DON", maHD, reason);
            return null;
        });
    }
}
