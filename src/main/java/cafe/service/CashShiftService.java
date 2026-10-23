package cafe.service;

import cafe.config.Database;
import cafe.dao.CashShiftDao;
import cafe.model.CashMovementKind;
import cafe.model.CashShift;
import cafe.model.CashShiftAdjustment;
import cafe.model.NhanVien;
import cafe.util.BusinessException;
import cafe.util.Check;
import cafe.util.Money;
import cafe.util.Permission;
import cafe.util.Session;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Serialized ownership and cash reconciliation for the single physical drawer per configured database. */
public final class CashShiftService {
    public record Summary(CashShift shift, CashShiftDao.Totals totals, BigDecimal expected,
                          BigDecimal adjustments, BigDecimal adjustedExpected, BigDecimal adjustedVariance) {
    }

    private final Database db;
    private final AuditService audit;
    private final CashShiftDao dao = new CashShiftDao();

    public CashShiftService(Database db, AuditService audit) {
        this.db = db;
        this.audit = audit;
    }

    /** Opens the one shared drawer. The singleton row lock prevents concurrent overlapping shifts. */
    public CashShift openShift(BigDecimal openingCash) {
        NhanVien me = Session.require(Permission.CASHIER_SHIFT);
        BigDecimal opening = Money.round(Check.nonNegative(openingCash, "Tiền đầu ca"));
        int id = db.tx(c -> {
            if (dao.lockActiveId(c) != null) {
                throw new BusinessException("Ngăn tiền đang thuộc một ca khác. Hãy hoàn tất bàn giao trước.");
            }
            int shiftId = dao.open(c, me.maNV(), opening, LocalDateTime.now());
            audit.log(c, "MO_CA_THU_NGAN", "CA_THU_NGAN", shiftId,
                    "Tiền đầu ca " + Money.format(opening) + "; chủ ca: " + me.tenNV());
            return shiftId;
        });
        return db.read(c -> dao.find(c, id));
    }

    public CashShift currentForMe() {
        NhanVien me = Session.requireLogin();
        return db.read(c -> dao.findActiveForEmployee(c, me.maNV()));
    }

    public CashShift activeShift() {
        Session.require(Permission.VIEW_REPORTS);
        return db.read(c -> {
            Integer id = dao.activeId(c);
            return id == null ? null : dao.find(c, id);
        });
    }

    public CashShift visibleActiveShift() {
        NhanVien me = Session.requireLogin();
        return Permission.VIEW_REPORTS.allows(me.vaiTro()) ? activeShift() : currentForMe();
    }

    public Summary summary(int shiftId) {
        NhanVien me = Session.requireLogin();
        return db.read(c -> {
            CashShift shift = dao.find(c, shiftId);
            if (shift == null) throw new BusinessException("Không tìm thấy ca thu ngân.");
            if (shift.maNV() != me.maNV() && !Permission.VIEW_REPORTS.allows(me.vaiTro())) {
                throw new cafe.util.AccessDeniedException("Bạn chỉ được xem ca của mình.");
            }
            CashShiftDao.Totals totals = dao.totals(c, shiftId);
            BigDecimal expected = shift.dangMo() || shift.tienKyVong() == null
                    ? totals.expected(shift.tienDauCa()) : shift.tienKyVong();
            BigDecimal adjustments = dao.adjustmentTotal(c, shiftId);
            BigDecimal adjustedExpected = expected.add(adjustments);
            BigDecimal adjustedVariance = shift.tienDem() == null ? null : shift.tienDem().subtract(adjustedExpected);
            return new Summary(shift, totals, expected, adjustments, adjustedExpected, adjustedVariance);
        });
    }

    /** Authorized, append-only correction to a closed shift; the original closeout snapshot is never overwritten. */
    public void recordClosedShiftCorrection(int shiftId, BigDecimal expectedCashImpact, String reason, String requestKey) {
        NhanVien me = Session.require(Permission.VIEW_REPORTS);
        BigDecimal impact = Money.round(Check.notNull(expectedCashImpact, "mức điều chỉnh"));
        if (impact.signum() == 0) throw new BusinessException("Mức điều chỉnh phải khác 0.");
        if (impact.abs().compareTo(new BigDecimal("1000000000000")) > 0) throw new BusinessException("Mức điều chỉnh quá lớn.");
        String why = Check.required(reason, "lý do điều chỉnh", 255);
        String key = Check.required(requestKey, "mã yêu cầu", 64);
        db.tx(c -> {
            if (!dao.lockShift(c, shiftId)) throw new BusinessException("Không tìm thấy ca thu ngân.");
            CashShift shift = dao.find(c, shiftId);
            if (shift == null || shift.dangMo()) throw new BusinessException("Chỉ được điều chỉnh ca đã đóng.");
            if (dao.adjustmentMatches(c, key, shiftId, impact)) return null;
            BigDecimal revisedExpected = shift.tienKyVong().add(dao.adjustmentTotal(c, shiftId)).add(impact);
            if (revisedExpected.signum() < 0) throw new BusinessException("Kỳ vọng sau điều chỉnh không được âm.");
            int adjustmentId = dao.insertAdjustment(c, shiftId, me.maNV(), LocalDateTime.now(), impact, why, key);
            BigDecimal revisedVariance = shift.tienDem().subtract(revisedExpected);
            audit.log(c, "DIEU_CHINH_DOI_SOAT_CA", "DIEU_CHINH_CA", adjustmentId,
                    "Ca #" + shiftId + "; tác động kỳ vọng " + Money.format(impact)
                            + "; lệch sau điều chỉnh " + Money.format(revisedVariance) + "; lý do: " + why);
            return null;
        });
    }

    public List<CashShiftAdjustment> adjustments(int shiftId) {
        Session.require(Permission.VIEW_REPORTS);
        return db.read(c -> {
            CashShift shift = dao.find(c, shiftId);
            if (shift == null) throw new BusinessException("Không tìm thấy ca thu ngân.");
            return dao.adjustments(c, shiftId);
        });
    }

    public List<CashShiftAdjustment> adjustmentHistory() {
        Session.require(Permission.VIEW_REPORTS);
        return db.read(dao::adjustments);
    }

    public List<CashShift> history() {
        NhanVien me = Session.requireLogin();
        boolean all = Permission.VIEW_REPORTS.allows(me.vaiTro());
        return db.read(c -> dao.findRecent(c, me.maNV(), all));
    }

    public void recordMovement(CashMovementKind kind, BigDecimal amount, String reason, String requestKey) {
        NhanVien me = Session.require(Permission.CASHIER_SHIFT);
        Check.notNull(kind, "loại biến động");
        BigDecimal positive = Money.round(Check.positive(amount, "Số tiền"));
        String why = Check.required(reason, "lý do", 255);
        String key = Check.required(requestKey, "mã yêu cầu", 64);
        db.tx(c -> {
            Integer id = dao.lockActiveId(c);
            if (id == null) throw new BusinessException("Mở ca trước khi ghi nhận biến động tiền mặt.");
            CashShift shift = dao.find(c, id);
            if (shift.maNV() != me.maNV()) throw new BusinessException("Ngăn tiền đang do nhân viên khác phụ trách.");
            if (dao.movementMatches(c, key, id, kind.name(), positive)) return null;
            int movementId = dao.insertMovement(c, id, me.maNV(), LocalDateTime.now(), kind.name(), positive, why, key);
            audit.log(c, "BIEN_DONG_TIEN_MAT", "BIEN_DONG_TIEN_MAT", movementId,
                    kind + " " + Money.format(positive) + "; lý do: " + why);
            return null;
        });
    }

    public Summary closeShift(BigDecimal countedCash, String varianceReason) {
        NhanVien me = Session.require(Permission.CASHIER_SHIFT);
        return closeShift(countedCash, varianceReason, false, me);
    }

    /** Manager-confirmed handover preserves open orders and their original employee ownership. */
    public Summary closeShiftForHandover(BigDecimal countedCash, String handoverReason) {
        NhanVien me = Session.require(Permission.VIEW_REPORTS);
        Check.required(handoverReason, "lý do bàn giao", 255);
        return closeShift(countedCash, handoverReason, true, me);
    }

    private Summary closeShift(BigDecimal countedCash, String varianceReason, boolean handover, NhanVien me) {
        BigDecimal counted = Money.round(Check.nonNegative(countedCash, "Tiền kiểm đếm cuối ca"));
        String note = Check.optional(varianceReason, "ghi chú", 255);
        return db.tx(c -> {
            Integer id = dao.lockActiveId(c);
            if (id == null) throw new BusinessException("Không có ca đang mở để đóng.");
            CashShift shift = dao.find(c, id);
            if (shift.maNV() != me.maNV() && !handover) throw new BusinessException("Chỉ nhân viên phụ trách ca mới được đóng ngăn tiền.");
            int openOrders = dao.countOpenOrders(c, me.maNV());
            if (handover) openOrders = dao.countOpenOrders(c, shift.maNV());
            if (openOrders > 0 && !handover) {
                throw new BusinessException("Còn " + openOrders + " hóa đơn đang mở của bạn. Hãy thanh toán, hủy hoặc nhờ quản lý xác nhận bàn giao.");
            }
            CashShiftDao.Totals totals = dao.totals(c, id);
            BigDecimal expected = totals.expected(shift.tienDauCa());
            BigDecimal variance = counted.subtract(expected);
            if (variance.signum() != 0 && note == null) {
                throw new BusinessException("Ghi rõ lý do khi tiền kiểm đếm lệch " + Money.format(variance) + ".");
            }
            if (handover && note == null) throw new BusinessException("Nhập lý do bàn giao ca.");
            dao.close(c, id, me.maNV(), LocalDateTime.now(), counted, expected, variance, note);
            audit.log(c, handover ? "BAN_GIAO_CA_THU_NGAN" : "DONG_CA_THU_NGAN", "CA_THU_NGAN", id,
                    "Kỳ vọng " + Money.format(expected) + "; đã đếm " + Money.format(counted)
                            + "; chênh lệch " + Money.format(variance) + "; hóa đơn mở được giữ nguyên: " + openOrders
                            + (note == null ? "" : "; lý do: " + note));
            return new Summary(dao.find(c, id), totals, expected, BigDecimal.ZERO, expected, variance);
        });
    }

    /** Called inside the payment transaction; acquiring the drawer lock first serializes payment vs closeout. */
    public static int lockOpenShiftForPayment(java.sql.Connection c, int employeeId) throws java.sql.SQLException {
        CashShiftDao dao = new CashShiftDao();
        Integer id = dao.lockActiveId(c);
        if (id == null) throw new BusinessException("Mở ca thu ngân trước khi thanh toán.");
        CashShift shift = dao.find(c, id);
        if (shift == null || !shift.dangMo() || shift.maNV() != employeeId) {
            throw new BusinessException("Ca thu ngân đang mở thuộc nhân viên khác.");
        }
        return id;
    }
}
