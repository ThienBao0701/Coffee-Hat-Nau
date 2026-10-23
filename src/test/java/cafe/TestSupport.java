package cafe;

import cafe.config.AppConfig;
import cafe.config.Database;
import cafe.model.NhanVien;
import cafe.model.Role;
import cafe.service.Services;
import cafe.util.Session;

import java.math.BigDecimal;
import java.util.Properties;
import java.util.UUID;

/** Fresh in-memory H2 (MySQL mode) database with schema + demo data, one per test class. */
public final class TestSupport {

    public static final char[] MANAGER_PW = "Manager123".toCharArray();
    public static final char[] STAFF_PW = "Staff1234".toCharArray();

    private TestSupport() {
    }

    public static Services newServices() {
        Properties p = new Properties();
        p.setProperty("db.url", "jdbc:h2:mem:t" + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1");
        AppConfig cfg = new AppConfig(p);
        Database db = Database.fromConfig(cfg);
        db.initializeIfEmpty(true);
        db.migrate();
        return new Services(cfg, db);
    }

    /** Creates the first manager ("admin") and an employee ("staff"); leaves nobody logged in. */
    public static void createAccounts(Services s) {
        Session.logout();
        s.auth.createInitialOwner("Chủ quán", "admin", MANAGER_PW, MANAGER_PW);
        s.auth.login("admin", MANAGER_PW.clone());
        char[] temp = "Temp12345".toCharArray();
        s.employees.create(new NhanVien(null, "Nhân Viên A", "Thu ngân", "0901111222", null, null, null, "Ca sáng",
                BigDecimal.ONE, new BigDecimal("6000000"), "staff", Role.NHAN_VIEN, false, true), temp.clone());
        Session.logout();
        // the employee replaces the temporary password, as the app forces at first login
        s.auth.login("staff", temp.clone());
        s.auth.changePassword(temp, STAFF_PW.clone(), STAFF_PW.clone());
        Session.logout();
    }

    public static NhanVien loginManager(Services s) {
        prepareShiftFor(s, "admin");
        NhanVien user = s.auth.login("admin", MANAGER_PW.clone());
        if (activeShiftId(s) == null) s.cashShifts.openShift(BigDecimal.ZERO);
        return user;
    }

    public static NhanVien loginStaff(Services s) {
        prepareShiftFor(s, "staff");
        NhanVien user = s.auth.login("staff", STAFF_PW.clone());
        if (activeShiftId(s) == null) s.cashShifts.openShift(BigDecimal.ZERO);
        return user;
    }

    /** Used by service tests that model a manager preparing an order for a later cashier handover. */
    public static NhanVien loginManagerWithoutShift(Services s) {
        return s.auth.login("admin", MANAGER_PW.clone());
    }

    private static Integer activeShiftId(Services s) {
        return s.db.read(c -> cafe.dao.Jdbc.one(c,
                "SELECT ma_ca_dang_mo FROM ngan_ke_toan WHERE ma_ngan=1", rs -> {
                    int value = rs.getInt(1);
                    return rs.wasNull() ? null : value;
                }));
    }

    private static void prepareShiftFor(Services s, String targetUsername) {
        Integer activeId = activeShiftId(s);
        if (activeId == null) return;
        int ownerId = s.db.read(c -> cafe.dao.Jdbc.one(c,
                "SELECT ma_nv FROM ca_thu_ngan WHERE ma_ca=?", rs -> rs.getInt(1), activeId));
        String ownerUsername = s.db.read(c -> cafe.dao.Jdbc.one(c,
                "SELECT ten_dang_nhap FROM nhan_vien WHERE ma_nv=?", rs -> rs.getString(1), ownerId));
        if (ownerUsername.equals(targetUsername)) return;
        int pending = s.db.read(c -> cafe.dao.Jdbc.count(c,
                "SELECT COUNT(*) FROM hoa_don WHERE ma_nv=? AND trang_thai='OPEN'", ownerId));
        if (pending > 0) return; // handover with unsettled orders must be explicit in the application
        Session.logout();
        // Fixture transitions use the explicit manager handover path. The shift owner may have
        // been disabled by the behavior under test and therefore cannot log in to close normally.
        s.auth.login("admin", MANAGER_PW.clone());
        var summary = s.cashShifts.summary(activeId);
        s.cashShifts.closeShiftForHandover(summary.expected(), "Chuyển ca trong fixture kiểm thử");
        s.auth.logout();
    }
}
