package cafe;

import cafe.model.CashMovementKind;
import cafe.model.PhuongThucThanhToan;
import cafe.service.CashShiftService;
import cafe.service.OrderService;
import cafe.service.Services;
import cafe.util.BusinessException;
import cafe.util.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CashShiftTest {
    private Services services;

    @BeforeEach
    void setUp() {
        services = TestSupport.newServices();
        TestSupport.createAccounts(services);
    }

    @AfterEach
    void tearDown() { Session.logout(); }

    @Test
    void expectedCashMatchesDocumentedExampleExactly() {
        var totals = new cafe.dao.CashShiftDao.Totals(new BigDecimal("1200000"), new BigDecimal("50000"),
                new BigDecimal("100000"), new BigDecimal("80000"), new BigDecimal("200000"));
        assertEquals(0, totals.expected(new BigDecimal("500000")).compareTo(new BigDecimal("1470000")));
    }

    @Test
    void paymentMethodAndCashRefundAffectDrawerOnlyWhenCashMoves() {
        TestSupport.loginStaff(services);
        int cardInvoice = order();
        services.orders.pay(new OrderService.PaymentRequest(cardInvoice, null, null, PhuongThucThanhToan.THE, null));
        int shiftId = services.cashShifts.currentForMe().maCa();
        BigDecimal opening = services.cashShifts.summary(shiftId).expected();
        assertEquals(0, opening.compareTo(BigDecimal.ZERO), "card payment is not physical cash");

        int cashInvoice = order();
        var paid = services.orders.pay(new OrderService.PaymentRequest(cashInvoice, null, null, PhuongThucThanhToan.TIEN_MAT,
                new BigDecimal("50000")));
        BigDecimal sale = paid.thanhToan().soTien();
        assertEquals(0, services.cashShifts.summary(shiftId).expected().compareTo(sale));

        Session.logout();
        TestSupport.loginManager(services);
        services.orders.refund(cashInvoice, "Khách trả món");
        int managerShift = services.cashShifts.currentForMe().maCa();
        assertEquals(0, services.cashShifts.summary(managerShift).expected().compareTo(sale.negate()));
    }

    @Test
    void duplicateMovementAndShiftTransitionsAreGuardedAndVarianceIsRetained() {
        TestSupport.loginStaff(services);
        var shift = services.cashShifts.currentForMe();
        assertThrows(BusinessException.class, () -> services.cashShifts.openShift(BigDecimal.ZERO));
        String key = UUID.randomUUID().toString();
        services.cashShifts.recordMovement(CashMovementKind.THEM, new BigDecimal("100000"), "Bổ sung tiền lẻ", key);
        services.cashShifts.recordMovement(CashMovementKind.THEM, new BigDecimal("100000"), "Bổ sung tiền lẻ", key);
        assertEquals(0, services.cashShifts.summary(shift.maCa()).expected().compareTo(new BigDecimal("100000")));
        assertThrows(BusinessException.class, () -> services.cashShifts.closeShift(new BigDecimal("90000"), null));
        var closed = services.cashShifts.closeShift(new BigDecimal("90000"), "Thiếu tiền khi kiểm đếm");
        assertEquals(0, closed.shift().chenhLech().compareTo(new BigDecimal("-10000")));
        assertThrows(BusinessException.class, () -> services.cashShifts.closeShift(BigDecimal.ZERO, "Đóng lần hai"));
        int unpaid = services.orders.openOrder(cafe.model.LoaiDon.MANG_DI, null);
        assertThrows(BusinessException.class, () -> services.orders.pay(new OrderService.PaymentRequest(unpaid, null, null,
                PhuongThucThanhToan.THE, null)));
    }

    @Test
    void managerCannotCreatePrivilegedAccountsEvenThroughDirectServiceCalls() {
        TestSupport.loginManager(services);
        int managerId = services.employees.create(new cafe.model.NhanVien(null, "Quản lý", null, null, null, null, null,
                null, BigDecimal.ONE, BigDecimal.ZERO, "manager2", cafe.model.Role.QUAN_LY, false, true),
                "TempManager123".toCharArray());
        Session.logout();
        var manager = services.auth.login("manager2", "TempManager123".toCharArray());
        services.auth.changePassword("TempManager123".toCharArray(), "ManagerNew123".toCharArray(), "ManagerNew123".toCharArray());
        assertEquals(managerId, Session.current().maNV());
        assertThrows(BusinessException.class, () -> services.employees.create(new cafe.model.NhanVien(null, "Quản lý khác", null,
                null, null, null, null, null, BigDecimal.ONE, BigDecimal.ZERO, "manager3", cafe.model.Role.QUAN_LY,
                false, true), "TempManager123".toCharArray()));
        assertThrows(BusinessException.class, () -> services.employees.create(new cafe.model.NhanVien(null, "Chủ mới", null,
                null, null, null, null, null, BigDecimal.ONE, BigDecimal.ZERO, "owner3", cafe.model.Role.CHU_CUA_HANG,
                false, true), "TempManager123".toCharArray()));
    }

    @Test
    void closedShiftCorrectionIsAppendOnlyAuthorizedAndIdempotent() {
        TestSupport.loginStaff(services);
        var original = services.cashShifts.currentForMe();
        services.cashShifts.recordMovement(CashMovementKind.THEM, new BigDecimal("20000"),
                "Thêm tiền kiểm tra đối soát", UUID.randomUUID().toString());
        var closed = services.cashShifts.closeShift(new BigDecimal("20000"), null);
        assertThrows(cafe.util.AccessDeniedException.class, () -> services.cashShifts.recordClosedShiftCorrection(
                original.maCa(), new BigDecimal("10000"), "Thiếu ghi nhận khoản chi", UUID.randomUUID().toString()));

        Session.logout();
        TestSupport.loginManager(services);
        assertThrows(BusinessException.class, () -> services.cashShifts.recordClosedShiftCorrection(original.maCa(),
                new BigDecimal("-30000"), "Không thể tạo số dư kỳ vọng âm", UUID.randomUUID().toString()));
        String requestKey = UUID.randomUUID().toString();
        services.cashShifts.recordClosedShiftCorrection(original.maCa(), new BigDecimal("-10000"),
                "Chi phí đã ghi nhận sau khi khóa ca", requestKey);
        services.cashShifts.recordClosedShiftCorrection(original.maCa(), new BigDecimal("-10000"),
                "Chi phí đã ghi nhận sau khi khóa ca", requestKey);

        var revised = services.cashShifts.summary(original.maCa());
        assertEquals(0, revised.expected().compareTo(closed.expected()), "original expected closeout is retained");
        assertEquals(0, revised.shift().chenhLech().compareTo(closed.shift().chenhLech()), "original variance is immutable");
        assertEquals(0, revised.adjustments().compareTo(new BigDecimal("-10000")));
        assertEquals(0, revised.adjustedExpected().compareTo(new BigDecimal("10000")));
        assertEquals(0, revised.adjustedVariance().compareTo(new BigDecimal("10000")));
        assertEquals(1, services.cashShifts.adjustments(original.maCa()).size());
        assertEquals(1, services.cashShifts.adjustmentHistory().size());
    }

    private int order() {
        int id = services.orders.openTableOrder(1);
        services.orders.addItem(id, 2, 2, 1, Map.of(), null);
        return id;
    }
}
