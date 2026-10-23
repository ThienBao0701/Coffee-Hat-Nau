package cafe;

import cafe.dao.InventoryDao;
import cafe.dao.Jdbc;
import cafe.model.BangGia;
import cafe.model.ChiTietPhieuNhap;
import cafe.model.ChiTietPhieuXuat;
import cafe.model.HoaDon;
import cafe.model.InvoiceDetail;
import cafe.model.KhachHang;
import cafe.model.KhuyenMai;
import cafe.model.LoaiDon;
import cafe.model.Mon;
import cafe.model.NguyenLieu;
import cafe.model.NhanVien;
import cafe.model.PhuongThucThanhToan;
import cafe.model.Reports;
import cafe.model.Role;
import cafe.model.TableStatus;
import cafe.model.TrangThaiBan;
import cafe.model.TrangThaiHoaDon;
import cafe.service.CatalogService;
import cafe.service.OrderService;
import cafe.service.PriceCalculator;
import cafe.service.Services;
import cafe.util.AccessDeniedException;
import cafe.util.BusinessException;
import cafe.util.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Business rules verified against a real (in-memory) database through the service layer. */
class ServiceIntegrationTest {

    // seed ids (see seed-demo.sql)
    static final int BAN_01 = 1, BAN_02 = 2, BAN_03 = 3, BAN_04 = 4, BAN_05 = 5, SAN_03_INACTIVE = 12;
    static final int CA_PHE_SUA = 2, BAC_XIU = 3, TRA_DAO = 6, CA_PHE_MUOI_INACTIVE = 14;
    static final int SIZE_M = 2, SIZE_L = 3;
    static final int SHOT_ESPRESSO = 4;
    static final int NL_CA_PHE = 1, NL_SUA_DAC = 2, NL_SUA_TUOI = 3, NL_DAO = 6;

    Services s;
    final LocalDate today = LocalDate.now();

    @BeforeEach
    void setUp() {
        s = TestSupport.newServices();
        TestSupport.createAccounts(s);
    }

    @AfterEach
    void tearDown() {
        Session.logout();
    }

    private static BigDecimal n(String v) {
        return new BigDecimal(v);
    }

    private BigDecimal stock(int maNL) {
        return s.inventory.findNguyenLieu().stream().filter(x -> x.maNL() == maNL).findFirst().orElseThrow().soLuongTon();
    }

    private int createPromotion10Percent() {
        s.promotions.saveKhuyenMai(new KhuyenMai(null, "Test 10%", 1, null, null, null, today.minusDays(1), today.plusDays(30),
                BigDecimal.ZERO, n("50000"), true));
        return s.promotions.findKhuyenMai().stream().filter(k -> k.tenKM().equals("Test 10%")).findFirst().orElseThrow().maKM();
    }

    /** Cà phê sữa M x2 + 1 shot espresso each (74,000) and Bạc xỉu L x1 (37,000) = 111,000. */
    private int openSampleOrder(int maBan) {
        int hd = s.orders.openTableOrder(maBan);
        s.orders.addItem(hd, CA_PHE_SUA, SIZE_M, 2, Map.of(SHOT_ESPRESSO, 1), "ít đá");
        s.orders.addItem(hd, BAC_XIU, SIZE_L, 1, Map.of(), null);
        return hd;
    }

    // ---------- setup & authentication ----------
    @Test
    void schemaInitialisationRunsOnlyOnEmptyDatabase() {
        assertFalse(s.db.initializeIfEmpty(true), "existing database must not be re-initialised");
    }

    @Test
    void initialSetupOnlyOnce() {
        assertFalse(s.auth.needsInitialSetup());
        assertThrows(BusinessException.class,
                () -> s.auth.createInitialManager("Kẻ lạ", "hacker", "Hacker1234".toCharArray(), "Hacker1234".toCharArray()));
    }

    @Test
    void loginSuccessAndFailure() {
        NhanVien m = TestSupport.loginManager(s);
        assertEquals(Role.CHU_CUA_HANG, m.vaiTro());
        assertEquals(m, Session.current());
        s.auth.logout();
        assertEquals(null, Session.current());

        assertThrows(BusinessException.class, () -> s.auth.login("admin", "wrong-pass1".toCharArray()));
        assertThrows(BusinessException.class, () -> s.auth.login("nobody", "Manager123".toCharArray()));
        assertThrows(BusinessException.class, () -> s.auth.login("", new char[0]));
        assertEquals(null, Session.current());
    }

    @Test
    void temporaryPasswordMustBeChangedBeforeAnyOtherOperation() {
        TestSupport.loginManager(s);
        int staffId = s.employees.findAll().stream().filter(e -> e.tenDangNhap().equals("staff")).findFirst().orElseThrow().maNV();
        s.employees.resetPassword(staffId, "Reset1234".toCharArray());
        s.auth.logout();

        NhanVien staff = s.auth.login("staff", "Reset1234".toCharArray());
        assertTrue(staff.phaiDoiMatKhau());
        // enforced by the service layer, not only by the login screen
        assertThrows(AccessDeniedException.class, () -> s.orders.openTableOrder(BAN_01));
        assertThrows(AccessDeniedException.class, () -> s.catalog.findMenu());
        s.auth.changePassword("Reset1234".toCharArray(), "NewPass999".toCharArray(), "NewPass999".toCharArray());
        assertFalse(Session.current().phaiDoiMatKhau());
        assertTrue(s.orders.openTableOrder(BAN_01) > 0);
        s.auth.logout();
        assertThrows(BusinessException.class, () -> s.auth.login("staff", "Reset1234".toCharArray()));
        assertNotNull(s.auth.login("staff", "NewPass999".toCharArray()));
    }

    @Test
    void lockedOrDemotedAccountLosesAccessWithoutLoggingOut() {
        TestSupport.loginStaff(s);
        assertFalse(s.catalog.findMenu().isEmpty());
        // another terminal (simulated with SQL) locks the account while this session is open
        s.db.tx(c -> Jdbc.update(c, "UPDATE nhan_vien SET trang_thai = FALSE WHERE ten_dang_nhap = 'staff'"));
        assertThrows(AccessDeniedException.class, () -> s.catalog.findMenu());
        assertEquals(null, Session.current(), "a locked account is logged out");

        TestSupport.loginManager(s);
        s.employees.create(new NhanVien(null, "Quản lý 2", null, null, null, null, null, null, BigDecimal.ONE, BigDecimal.ZERO,
                "boss2", Role.QUAN_LY, false, true), "Boss12345".toCharArray());
        s.auth.logout();
        s.auth.login("boss2", "Boss12345".toCharArray());
        s.auth.changePassword("Boss12345".toCharArray(), "Boss67890".toCharArray(), "Boss67890".toCharArray());
        assertFalse(s.employees.findAll().isEmpty());
        s.db.tx(c -> Jdbc.update(c, "UPDATE nhan_vien SET vai_tro = 'NHAN_VIEN' WHERE ten_dang_nhap = 'boss2'"));
        assertThrows(AccessDeniedException.class, () -> s.employees.findAll(), "demotion applies immediately");
        assertFalse(s.catalog.findMenu().isEmpty(), "still allowed to sell as an employee");
    }

    @Test
    void cashierCanPayAndSeeAnOrderOpenedBySomeoneElse() {
        TestSupport.loginManagerWithoutShift(s);
        int hd = openSampleOrder(BAN_01);
        s.auth.logout();
        TestSupport.loginStaff(s);
        InvoiceDetail paid = s.orders.pay(new OrderService.PaymentRequest(hd, null, null, PhuongThucThanhToan.THE, null));
        assertEquals(TrangThaiHoaDon.PAID, paid.hoaDon().trangThai());
        assertEquals(1, s.orders.search(today, today, null, null).size(), "the cashier sees invoices they collected");
        assertNotNull(s.orders.getInvoice(hd));
    }

    @Test
    void concurrentPaymentOfTheSameInvoiceSucceedsOnce() throws Exception {
        TestSupport.loginStaff(s);
        int hd = openSampleOrder(BAN_01);
        List<String> results = runConcurrently(2,
                () -> s.orders.pay(new OrderService.PaymentRequest(hd, null, null, PhuongThucThanhToan.THE, null)));
        assertEquals(1, results.stream().filter("OK"::equals).count(), results.toString());
        assertTrue(results.stream().allMatch(r -> r.equals("OK") || r.startsWith("BusinessException")), "losers get a clear message: " + results);
        assertEquals(1, (int) s.db.read(c -> Jdbc.count(c, "SELECT COUNT(*) FROM thanh_toan WHERE ma_hd = ?", hd)));
        BigDecimal coffee = s.db.read(c -> Jdbc.one(c, "SELECT COALESCE(SUM(so_luong), 0) FROM bien_dong_kho WHERE tham_chieu = ? AND ma_nl = ?",
                rs -> rs.getBigDecimal(1), "HD" + hd, NL_CA_PHE));
        assertEquals(0, coffee.compareTo(n("-53")), "stock deducted once");
    }

    @Test
    void concurrentOpeningOfTheSameTableSucceedsOnce() throws Exception {
        TestSupport.loginStaff(s);
        List<String> results = runConcurrently(4, () -> s.orders.openTableOrder(BAN_05));
        assertEquals(1, results.stream().filter("OK"::equals).count(), results.toString());
        assertTrue(results.stream().allMatch(r -> r.equals("OK") || r.startsWith("BusinessException")), "losers get a clear message: " + results);
        assertEquals(1, (int) s.db.read(c -> Jdbc.count(c, "SELECT COUNT(*) FROM hoa_don WHERE ma_ban = ? AND trang_thai = 'OPEN'", BAN_05)));
    }

    /** Starts the same action on n threads at once; returns "OK" or the exception class per thread. */
    private static List<String> runConcurrently(int n, Runnable action) throws Exception {
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(n);
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        List<java.util.concurrent.Future<String>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    action.run();
                    return "OK";
                } catch (RuntimeException e) {
                    return e.getClass().getSimpleName() + ": " + e.getMessage();
                }
            }));
        }
        start.countDown();
        List<String> out = new java.util.ArrayList<>();
        for (java.util.concurrent.Future<String> f : futures) {
            out.add(f.get(30, java.util.concurrent.TimeUnit.SECONDS));
        }
        pool.shutdown();
        return out;
    }

    @Test
    void reportDayBoundariesAreHalfOpen() {
        TestSupport.loginManager(s);
        int a = openSampleOrder(BAN_01);
        s.orders.pay(new OrderService.PaymentRequest(a, null, null, PhuongThucThanhToan.THE, null));
        int b = openSampleOrder(BAN_02);
        s.orders.pay(new OrderService.PaymentRequest(b, null, null, PhuongThucThanhToan.THE, null));
        LocalDate yesterday = today.minusDays(1);
        s.db.tx(c -> Jdbc.update(c, "UPDATE thanh_toan SET thoi_diem = ? WHERE ma_hd = ?", yesterday.atTime(23, 59, 59), a));
        s.db.tx(c -> Jdbc.update(c, "UPDATE thanh_toan SET thoi_diem = ? WHERE ma_hd = ?", today.atStartOfDay(), b));
        assertEquals(1, s.reports.summary(yesterday, yesterday).soHoaDon());
        assertEquals(1, s.reports.summary(today, today).soHoaDon());
        assertEquals(2, s.reports.summary(yesterday, today).soHoaDon());
        assertEquals(2, s.reports.byDay(yesterday, today).size());
    }

    @Test
    void onlyTodaysReservationsCanBeCheckedIn() {
        TestSupport.loginStaff(s);
        int soon = s.customers.createReservation(null, "Khách Sắp Đến", "0977000555", BAN_04, LocalDateTime.now().plusMinutes(30), 2, null);
        int stale = s.customers.createReservation(null, "Khách Cũ", "0977000666", BAN_05, LocalDateTime.now().plusMinutes(30), 2, null);
        s.db.tx(c -> Jdbc.update(c, "UPDATE don_dat_hang SET thoi_diem_dat = ? WHERE ma_ddh = ?", LocalDateTime.now().minusDays(3), stale));
        assertThrows(BusinessException.class, () -> s.orders.checkInReservation(stale));
        int hd = s.orders.checkInReservation(soon);
        assertEquals(TrangThaiBan.CO_KHACH, status(BAN_04));
        assertNotNull(s.orders.getInvoice(hd).hoaDon().tenKH());
        assertThrows(BusinessException.class, () -> s.orders.checkInReservation(soon), "already completed");
    }

    @Test
    void deactivatedAccountCannotLogIn() {
        TestSupport.loginManager(s);
        NhanVien staff = s.employees.findAll().stream().filter(e -> e.tenDangNhap().equals("staff")).findFirst().orElseThrow();
        s.employees.update(new NhanVien(staff.maNV(), staff.tenNV(), staff.chucVu(), staff.soDienThoai(), staff.ngaySinh(), staff.diaChi(),
                staff.bangCap(), staff.gioLam(), staff.heSoLuong(), staff.luongCoBan(), staff.tenDangNhap(), staff.vaiTro(), false, false));
        s.auth.logout();
        assertThrows(BusinessException.class, () -> TestSupport.loginStaff(s));
    }

    @Test
    void lastActiveManagerCannotBeRemovedOrSelfDemoted() {
        NhanVien me = TestSupport.loginManager(s);
        NhanVien demoted = new NhanVien(me.maNV(), me.tenNV(), me.chucVu(), me.soDienThoai(), me.ngaySinh(), me.diaChi(), me.bangCap(),
                me.gioLam(), me.heSoLuong(), me.luongCoBan(), me.tenDangNhap(), Role.NHAN_VIEN, false, true);
        assertThrows(BusinessException.class, () -> s.employees.update(demoted));
    }

    @Test
    void duplicateUsernameRejected() {
        TestSupport.loginManager(s);
        assertThrows(BusinessException.class, () -> s.employees.create(new NhanVien(null, "Trùng", null, null, null, null, null, null,
                BigDecimal.ONE, BigDecimal.ZERO, "STAFF", Role.NHAN_VIEN, false, true), "Abcdefg123".toCharArray()));
    }

    // ---------- authorization ----------
    @Test
    void employeeCannotUseManagementFunctions() {
        TestSupport.loginStaff(s);
        assertThrows(AccessDeniedException.class, () -> s.employees.findAll());
        assertThrows(AccessDeniedException.class, () -> s.catalog.saveMon(new Mon(null, "Món lậu", 1, null, true)));
        assertThrows(AccessDeniedException.class, () -> s.inventory.dieuChinhTon(NL_CA_PHE, n("1"), "test"));
        assertThrows(AccessDeniedException.class, () -> s.promotions.saveThue(new cafe.model.Thue(null, "X", n("1"), today, null)));
        assertThrows(AccessDeniedException.class, () -> s.reports.summary(today, today));
        assertThrows(AccessDeniedException.class, () -> s.orders.refund(1, "x"));
        assertThrows(AccessDeniedException.class, () -> s.audit.find(today, today));
        // daily operations are allowed
        assertFalse(s.catalog.findMenu().isEmpty());
        assertFalse(s.tables.findTableStatuses().isEmpty());
    }

    @Test
    void notLoggedInIsDenied() {
        Session.logout();
        assertThrows(AccessDeniedException.class, () -> s.orders.openTableOrder(BAN_01));
        assertThrows(AccessDeniedException.class, () -> s.catalog.findMenu());
    }

    @Test
    void employeeSeesOnlyOwnClosedInvoicesAndMaskedIdNumbers() {
        TestSupport.loginManager(s);
        s.customers.save(new KhachHang(null, "Khách CMND", null, "079123456789", "0909000111", null));
        int hd = openSampleOrder(BAN_01);
        s.orders.pay(new OrderService.PaymentRequest(hd, null, null, PhuongThucThanhToan.CHUYEN_KHOAN, null));
        s.auth.logout();

        TestSupport.loginStaff(s);
        assertTrue(s.orders.search(today, today, null, null).isEmpty(), "staff must not list the manager's invoices");
        assertThrows(AccessDeniedException.class, () -> s.orders.getInvoice(hd));
        KhachHang k = s.customers.search("0909000111").get(0);
        assertEquals("********6789", k.soCMND());
        // editing the customer must not overwrite the real number with the masked one
        s.customers.save(new KhachHang(k.maKH(), "Khách CMND (sửa)", null, k.soCMND(), k.soDienThoai(), null));
        s.auth.logout();
        TestSupport.loginManager(s);
        assertEquals("079123456789", s.customers.search("0909000111").get(0).soCMND());
    }

    // ---------- orders & payment ----------
    @Test
    void fullOrderPaymentFlowWithToppingDiscountAndTax() {
        TestSupport.loginManager(s);
        int km = createPromotion10Percent();
        s.auth.logout();
        TestSupport.loginStaff(s);

        int hd = openSampleOrder(BAN_01);
        assertEquals(TrangThaiBan.CO_KHACH, status(BAN_01));
        InvoiceDetail open = s.orders.getInvoice(hd);
        assertEquals(0, open.tamTinh().compareTo(n("111000")));
        assertEquals(0, open.chiTiet().get(0).tinhTienTopping().compareTo(n("16000")));

        PriceCalculator.Totals preview = s.orders.preview(hd, km);
        assertEquals(0, preview.tongTien().compareTo(n("107892")));

        InvoiceDetail paid = s.orders.pay(new OrderService.PaymentRequest(hd, km, null, PhuongThucThanhToan.TIEN_MAT, n("200000")));
        HoaDon h = paid.hoaDon();
        assertEquals(TrangThaiHoaDon.PAID, h.trangThai());
        assertEquals(0, h.tamTinh().compareTo(n("111000")));
        assertEquals(0, h.tienGiam().compareTo(n("11100")));
        assertEquals(0, h.tienThue().compareTo(n("7992")));
        assertEquals(0, h.tongTien().compareTo(n("107892")));
        assertEquals(0, h.thueSuat().compareTo(n("8")));
        assertEquals("Test 10%", h.tenKM());
        assertEquals(0, paid.thanhToan().tienThoi().compareTo(n("92108")));
        assertEquals(TrangThaiBan.TRONG, status(BAN_01), "table is free once the invoice is paid");
    }

    @Test
    void invoiceCannotBePaidTwice() {
        TestSupport.loginStaff(s);
        int hd = openSampleOrder(BAN_01);
        OrderService.PaymentRequest req = new OrderService.PaymentRequest(hd, null, null, PhuongThucThanhToan.THE, null);
        s.orders.pay(req);
        assertThrows(BusinessException.class, () -> s.orders.pay(req));
        int payments = s.db.read(c -> Jdbc.count(c, "SELECT COUNT(*) FROM thanh_toan WHERE ma_hd = ?", hd));
        assertEquals(1, payments);
        BigDecimal coffeeMovements = s.db.read(c -> Jdbc.one(c,
                "SELECT COALESCE(SUM(so_luong), 0) FROM bien_dong_kho WHERE tham_chieu = ? AND ma_nl = ?", rs -> rs.getBigDecimal(1), "HD" + hd, NL_CA_PHE));
        assertEquals(0, coffeeMovements.compareTo(n("-53")), "stock consumed exactly once");
    }

    @Test
    void cashBelowTotalAndEmptyOrderAreRejected() {
        TestSupport.loginStaff(s);
        int empty = s.orders.openTableOrder(BAN_02);
        assertThrows(BusinessException.class, () -> s.orders.pay(new OrderService.PaymentRequest(empty, null, null, PhuongThucThanhToan.THE, null)));
        int hd = openSampleOrder(BAN_01);
        assertThrows(BusinessException.class, () -> s.orders.pay(new OrderService.PaymentRequest(hd, null, null, PhuongThucThanhToan.TIEN_MAT, n("1000"))));
        assertEquals(TrangThaiHoaDon.OPEN, s.orders.getInvoice(hd).hoaDon().trangThai());
    }

    @Test
    void tableCannotHaveTwoOpenOrders() {
        TestSupport.loginStaff(s);
        s.orders.openTableOrder(BAN_01);
        assertThrows(BusinessException.class, () -> s.orders.openTableOrder(BAN_01));
        assertThrows(BusinessException.class, () -> s.orders.openTableOrder(SAN_03_INACTIVE));
    }

    @Test
    void inactiveProductsCannotBeOrderedButHistoryStaysReadable() {
        TestSupport.loginStaff(s);
        int hd = s.orders.openTableOrder(BAN_01);
        assertThrows(BusinessException.class, () -> s.orders.addItem(hd, CA_PHE_MUOI_INACTIVE, SIZE_M, 1, Map.of(), null));
        assertTrue(s.catalog.findMenu().stream().noneMatch(m -> m.mon().maMon() == CA_PHE_MUOI_INACTIVE));
        assertThrows(BusinessException.class, () -> s.orders.addItem(hd, CA_PHE_SUA, SIZE_M, 0, Map.of(), null));
    }

    @Test
    void quantityUpdateAndLineRemoval() {
        TestSupport.loginStaff(s);
        int hd = openSampleOrder(BAN_01);
        int line = s.orders.getInvoice(hd).chiTiet().get(1).maCTHD();
        s.orders.capNhatSoLuong(line, 3);
        assertEquals(0, s.orders.getInvoice(hd).tamTinh().compareTo(n("185000")));   // 74,000 + 3 x 37,000
        s.orders.removeLine(line);
        assertEquals(1, s.orders.getInvoice(hd).chiTiet().size());
    }

    @Test
    void historicalInvoicesUnchangedAfterPriceAndPromotionChanges() {
        TestSupport.loginManager(s);
        int km = createPromotion10Percent();
        int hd = openSampleOrder(BAN_01);
        s.orders.pay(new OrderService.PaymentRequest(hd, km, null, PhuongThucThanhToan.THE, null));

        // raise every price of the current list and change the promotion
        BangGia current = s.catalog.findBangGia().stream().filter(b -> b.apDungVao(today)).findFirst().orElseThrow();
        List<CatalogService.PriceEntry> raised = s.catalog.findChiTietBangGia(current.maBangGia()).stream()
                .map(ct -> new CatalogService.PriceEntry(ct.maMon(), ct.maSize(), ct.giaBan().add(n("10000")))).toList();
        s.catalog.savePrices(current.maBangGia(), raised);
        s.promotions.saveKhuyenMai(new KhuyenMai(km, "Đổi tên", 2, null, null, null, today, today.plusDays(1), BigDecimal.ZERO, null, false));

        InvoiceDetail after = s.orders.getInvoice(hd);
        assertEquals(0, after.chiTiet().get(0).donGia().compareTo(n("29000")));
        assertEquals(0, after.hoaDon().tongTien().compareTo(n("107892")));
        assertEquals("Test 10%", after.hoaDon().tenKM());
        // new orders use the new price
        int hd2 = s.orders.openTableOrder(BAN_02);
        s.orders.addItem(hd2, CA_PHE_SUA, SIZE_M, 1, Map.of(), null);
        assertEquals(0, s.orders.getInvoice(hd2).chiTiet().get(0).donGia().compareTo(n("39000")));
    }

    @Test
    void transferAndMerge() {
        TestSupport.loginStaff(s);
        int a = openSampleOrder(BAN_01);
        s.orders.transferTable(a, BAN_02);
        assertEquals(TrangThaiBan.TRONG, status(BAN_01));
        assertEquals(TrangThaiBan.CO_KHACH, status(BAN_02));

        int b = s.orders.openTableOrder(BAN_03);
        s.orders.addItem(b, TRA_DAO, SIZE_M, 1, Map.of(), null);
        assertThrows(BusinessException.class, () -> s.orders.transferTable(a, BAN_03), "occupied target needs merge");
        s.orders.mergeOrders(b, a);
        assertEquals(3, s.orders.getInvoice(a).chiTiet().size());
        assertEquals(TrangThaiHoaDon.CANCELLED, s.orders.getInvoice(b).hoaDon().trangThai());
        assertEquals(TrangThaiBan.TRONG, status(BAN_03));
    }

    @Test
    void cancelOpenOrderAndRefundPaidInvoice() {
        TestSupport.loginStaff(s);
        int open = openSampleOrder(BAN_01);
        assertThrows(BusinessException.class, () -> s.orders.cancelOrder(open, " "), "reason required");
        s.orders.cancelOrder(open, "Khách đổi ý");
        assertEquals(TrangThaiBan.TRONG, status(BAN_01));
        assertThrows(BusinessException.class, () -> s.orders.addItem(open, CA_PHE_SUA, SIZE_M, 1, Map.of(), null));
        s.auth.logout();

        TestSupport.loginManager(s);
        int hd = openSampleOrder(BAN_02);
        s.orders.pay(new OrderService.PaymentRequest(hd, null, null, PhuongThucThanhToan.THE, null));
        BigDecimal coffeeAfterSale = stock(NL_CA_PHE);
        s.orders.refund(hd, "Pha sai món");
        assertEquals(TrangThaiHoaDon.REFUNDED, s.orders.getInvoice(hd).hoaDon().trangThai());
        assertEquals(0, stock(NL_CA_PHE).compareTo(coffeeAfterSale), "refund does not move stock");
        assertThrows(BusinessException.class, () -> s.orders.refund(hd, "lần 2"));
    }

    @Test
    void takeawayAndDeliveryOrders() {
        TestSupport.loginStaff(s);
        assertThrows(BusinessException.class, () -> s.orders.openOrder(LoaiDon.GIAO_HANG, ""));
        int d = s.orders.openOrder(LoaiDon.GIAO_HANG, "15 Lê Lợi, Q1");
        int t = s.orders.openOrder(LoaiDon.MANG_DI, null);
        assertEquals(2, s.orders.findOpenNonTableOrders().size());
        s.orders.addItem(t, CA_PHE_SUA, SIZE_M, 1, Map.of(), null);
        s.orders.pay(new OrderService.PaymentRequest(t, null, null, PhuongThucThanhToan.TIEN_MAT, n("50000")));
        assertEquals(1, s.orders.findOpenNonTableOrders().size());
        assertEquals("15 Lê Lợi, Q1", s.orders.getInvoice(d).hoaDon().diaChiGiao());
    }

    private TrangThaiBan status(int maBan) {
        return s.tables.findTableStatuses().stream().filter(t -> t.ban().maBan() == maBan).map(TableStatus::trangThai).findFirst().orElseThrow();
    }

    // ---------- inventory ----------
    @Test
    void paymentConsumesRecipeIngredientsBySize() {
        TestSupport.loginManager(s);
        int hd = openSampleOrder(BAN_01);
        s.orders.pay(new OrderService.PaymentRequest(hd, null, null, PhuongThucThanhToan.THE, null));
        // Cà phê sữa M x2: coffee 20x1.0x2=40, condensed milk 60; Bạc xỉu L x1: coffee 10x1.3=13, condensed 26, fresh milk 104
        assertEquals(0, stock(NL_CA_PHE).compareTo(n("4947")));
        assertEquals(0, stock(NL_SUA_DAC).compareTo(n("2914")));
        assertEquals(0, stock(NL_SUA_TUOI).compareTo(n("7896")));
        assertStockMatchesMovements();
    }

    @Test
    void insufficientStockBlocksPaymentWithoutPartialChanges() {
        TestSupport.loginStaff(s);
        int hd = s.orders.openTableOrder(BAN_01);
        s.orders.addItem(hd, CA_PHE_SUA, SIZE_M, 1, Map.of(), null);
        s.orders.addItem(hd, TRA_DAO, SIZE_M, 9, Map.of(), null);   // needs 450 g peaches, only 400 in stock
        BusinessException ex = assertThrows(BusinessException.class,
                () -> s.orders.pay(new OrderService.PaymentRequest(hd, null, null, PhuongThucThanhToan.THE, null)));
        assertTrue(ex.getMessage().contains("Đào ngâm"), ex.getMessage());
        s.auth.logout();
        TestSupport.loginManager(s);
        assertEquals(TrangThaiHoaDon.OPEN, s.orders.getInvoice(hd).hoaDon().trangThai());
        assertEquals(0, stock(NL_CA_PHE).compareTo(n("5000")), "coffee deduction rolled back");
        assertEquals(0, stock(NL_DAO).compareTo(n("400")));
    }

    @Test
    void receiptIssueCancellationAndTraceability() {
        TestSupport.loginManager(s);
        int pn = s.inventory.taoPhieuNhap(1, "Nhập tuần", List.of(
                new ChiTietPhieuNhap(NL_CA_PHE, null, null, n("1000"), n("350")),
                new ChiTietPhieuNhap(NL_DAO, null, null, n("2000"), n("60"))));
        assertEquals(0, stock(NL_CA_PHE).compareTo(n("6000")));
        assertEquals(0, s.inventory.findPhieuNhap(pn).tongTien().compareTo(n("470000")));

        // issuing more than in stock fails and changes nothing (first line would have succeeded)
        assertThrows(BusinessException.class, () -> s.inventory.taoPhieuXuat("Hư hỏng", List.of(
                new ChiTietPhieuXuat(NL_CA_PHE, null, null, n("10"), n("350")),
                new ChiTietPhieuXuat(NL_SUA_DAC, null, null, n("999999"), n("1")))));
        assertEquals(0, stock(NL_CA_PHE).compareTo(n("6000")));

        int px = s.inventory.taoPhieuXuat("Hết hạn", List.of(new ChiTietPhieuXuat(NL_CA_PHE, null, null, n("500"), n("350"))));
        assertEquals(0, stock(NL_CA_PHE).compareTo(n("5500")));
        s.inventory.huyPhieuXuat(px, "Nhập nhầm");
        assertEquals(0, stock(NL_CA_PHE).compareTo(n("6000")));
        s.inventory.huyPhieuNhap(pn, "Sai nhà cung cấp");
        assertEquals(0, stock(NL_CA_PHE).compareTo(n("5000")));
        assertThrows(BusinessException.class, () -> s.inventory.huyPhieuNhap(pn, "lần 2"));

        s.inventory.dieuChinhTon(NL_CA_PHE, n("4980"), "Kiểm kê cuối ngày");
        assertEquals(0, stock(NL_CA_PHE).compareTo(n("4980")));
        assertThrows(BusinessException.class, () -> s.inventory.dieuChinhTon(NL_CA_PHE, n("-1"), "âm"));
        assertStockMatchesMovements();
    }

    @Test
    void cancellingReceiptFailsWhenStockAlreadyUsed() {
        TestSupport.loginManager(s);
        s.inventory.saveNguyenLieu(new NguyenLieu(null, "Siro bạc hà", "ml", null, n("100"), true), BigDecimal.ZERO);
        int id = s.inventory.findNguyenLieu().stream().filter(x -> x.tenNL().equals("Siro bạc hà")).findFirst().orElseThrow().maNL();
        int pn = s.inventory.taoPhieuNhap(1, null, List.of(new ChiTietPhieuNhap(id, null, null, n("500"), n("100"))));
        s.inventory.taoPhieuXuat("Dùng thử", List.of(new ChiTietPhieuXuat(id, null, null, n("300"), n("100"))));
        assertThrows(BusinessException.class, () -> s.inventory.huyPhieuNhap(pn, "hủy"));
        assertEquals(0, stock(id).compareTo(n("200")));
    }

    @Test
    void lowStockAlert() {
        TestSupport.loginManager(s);
        List<NguyenLieu> low = s.inventory.findLowStock();
        assertTrue(low.stream().anyMatch(x -> x.maNL() == NL_DAO), "Đào ngâm 400 <= 500 must be flagged");
        assertTrue(low.stream().noneMatch(x -> x.maNL() == NL_CA_PHE));
        assertNotNull(s.reports.dashboard().lowStock());
    }

    private void assertStockMatchesMovements() {
        InventoryDao dao = new InventoryDao();
        for (NguyenLieu nl : s.inventory.findNguyenLieu()) {
            BigDecimal sum = s.db.read(c -> dao.sumMovements(c, nl.maNL()));
            assertEquals(0, sum.compareTo(nl.soLuongTon()), "stock of " + nl.tenNL() + " must equal the sum of its movements");
        }
    }

    // ---------- reservations ----------
    @Test
    void reservationConflictsAreDetected() {
        TestSupport.loginStaff(s);
        LocalDateTime at = LocalDate.now().plusDays(1).atTime(19, 0);
        int r1 = s.customers.createReservation(null, "Khách Đặt", "0977000111", BAN_03, at, 4, "Sinh nhật");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> s.customers.createReservation(null, "Khách Khác", "0977000222", BAN_03, at.plusMinutes(60), 2, null));
        assertTrue(ex.getMessage().contains("đã được đặt"));
        s.customers.createReservation(null, "Khách Khác", "0977000222", BAN_03, at.plusMinutes(120), 2, null);   // after the 2h window
        s.customers.createReservation(null, "Khách Khác", "0977000222", BAN_02, at, 2, null);                    // other table

        assertThrows(BusinessException.class, () -> s.customers.createReservation(null, "A", "0977000333", BAN_01, LocalDateTime.now().minusHours(1), 2, null));
        assertThrows(BusinessException.class, () -> s.customers.createReservation(null, "A", "0977000333", BAN_01, at, 10, null), "too many people");

        s.customers.cancelReservation(r1);
        s.customers.createReservation(null, "Khách Mới", "0977000444", BAN_03, at, 2, null);   // slot free again
        assertThrows(BusinessException.class, () -> s.customers.confirmReservation(r1), "cancelled cannot be confirmed");
        // the same phone is reused, not duplicated
        assertEquals(1, s.customers.search("0977000222").size());
    }

    // ---------- price lists ----------
    @Test
    void priceListPeriodsNeverOverlap() {
        TestSupport.loginManager(s);
        BangGia current = s.catalog.findBangGia().stream().filter(b -> b.apDungVao(today)).findFirst().orElseThrow();
        assertThrows(BusinessException.class, () -> s.catalog.saveBangGia(new BangGia(null, "Quá khứ", today.minusDays(3), null), null));
        assertThrows(BusinessException.class,
                () -> s.catalog.saveBangGia(new BangGia(null, "Có hạn trùng", today.plusDays(5), today.plusDays(10)), null));

        int next = s.catalog.saveBangGia(new BangGia(null, "Giá mới", today.plusDays(7), null), current.maBangGia());
        BangGia closed = s.catalog.findBangGia().stream().filter(b -> b.maBangGia().equals(current.maBangGia())).findFirst().orElseThrow();
        assertEquals(today.plusDays(6), closed.ngayKetThuc(), "running list auto-closes the day before");
        assertEquals(s.catalog.findChiTietBangGia(current.maBangGia()).size(), s.catalog.findChiTietBangGia(next).size());
        assertThrows(BusinessException.class, () -> s.catalog.savePrices(next, List.of(
                new CatalogService.PriceEntry(CA_PHE_SUA, SIZE_M, n("1")), new CatalogService.PriceEntry(CA_PHE_SUA, SIZE_M, n("2")))));
        s.catalog.deleteBangGia(next);
        assertThrows(BusinessException.class, () -> s.catalog.deleteBangGia(current.maBangGia()), "a used price list is history");
    }

    // ---------- reports ----------
    @Test
    void revenueReportUsesConsistentDefinitions() {
        TestSupport.loginManager(s);
        int km = createPromotion10Percent();
        int a = openSampleOrder(BAN_01);
        s.orders.pay(new OrderService.PaymentRequest(a, km, null, PhuongThucThanhToan.THE, null));        // 111,000 - 11,100 + 7,992
        int b = s.orders.openTableOrder(BAN_02);
        s.orders.addItem(b, CA_PHE_SUA, SIZE_M, 1, Map.of(), null);
        s.orders.pay(new OrderService.PaymentRequest(b, null, null, PhuongThucThanhToan.THE, null));      // 29,000 + 2,320
        int c = openSampleOrder(BAN_03);
        s.orders.cancelOrder(c, "test");                                                                   // ignored
        s.orders.refund(b, "Khách phàn nàn");

        Reports.RevenueSummary r = s.reports.summary(today, today);
        assertEquals(2, r.soHoaDon());
        assertEquals(0, r.doanhSo().compareTo(n("140000")));
        assertEquals(0, r.giamGia().compareTo(n("11100")));
        assertEquals(0, r.thue().compareTo(n("10312")));
        assertEquals(1, r.soHoaDonHoan());
        assertEquals(0, r.hoanTien().compareTo(n("29000")));
        assertEquals(0, r.doanhThuThuan().compareTo(n("99900")));
        assertEquals(0, r.tongThu().compareTo(n("139212")));

        assertEquals(1, s.reports.byDay(today, today).size());
        Reports.ProductSales top = s.reports.productSales(today, today).get(0);
        assertEquals("Cà phê sữa", top.tenMon());
        assertEquals(3, top.soLuong());
        assertEquals(1, s.reports.employeeSales(today, today).size());
        assertTrue(s.reports.summary(today.plusDays(1), today.plusDays(2)).soHoaDon() == 0);

        Reports.StockRow coffee = s.reports.stock(today, today).stream().filter(x -> x.tenNL().equals("Cà phê hạt rang")).findFirst().orElseThrow();
        assertEquals(0, coffee.tonDau().compareTo(n("5000")));
        assertEquals(0, coffee.xuat().compareTo(n("73")));    // 53 + 20
        assertEquals(0, coffee.tonCuoi().compareTo(n("4927")));
    }

    @Test
    void auditLogRecordsSensitiveActions() {
        TestSupport.loginManager(s);
        int hd = openSampleOrder(BAN_01);
        s.orders.pay(new OrderService.PaymentRequest(hd, null, null, PhuongThucThanhToan.THE, null));
        s.orders.refund(hd, "test");
        List<String> actions = s.audit.find(today, today).stream().map(cafe.model.NhatKy::hanhDong).toList();
        assertTrue(actions.containsAll(List.of("THIET_LAP_BAN_DAU", "TAO_NHAN_VIEN", "DANG_NHAP", "THANH_TOAN", "HOAN_TIEN")), actions.toString());
    }

    @Test
    void validationMessagesForMissingFields() {
        TestSupport.loginManager(s);
        assertThrows(BusinessException.class, () -> s.catalog.saveMon(new Mon(null, "  ", 1, null, true)));
        assertThrows(BusinessException.class, () -> s.customers.save(new KhachHang(null, "Tên", null, "123", null, null)));
        assertThrows(BusinessException.class, () -> s.customers.save(new KhachHang(null, "Tên", null, null, "12345", null)));
        assertThrows(BusinessException.class, () -> s.inventory.taoPhieuNhap(1, null, List.of()));
        assertThrows(BusinessException.class, () -> s.promotions.saveLoai(new cafe.model.LoaiKhuyenMai(null, "Sai", n("10"), n("1000"))));
        assertThrows(BusinessException.class, () -> s.promotions.saveThue(new cafe.model.Thue(null, "Trùng", n("10"), today, null)));
    }
}
