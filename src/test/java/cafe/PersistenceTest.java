package cafe;

import cafe.config.AppConfig;
import cafe.config.Database;
import cafe.model.HoaDon;
import cafe.model.PhuongThucThanhToan;
import cafe.model.TrangThaiHoaDon;
import cafe.dao.Jdbc;
import cafe.service.OrderService;
import cafe.service.Services;
import cafe.util.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Data written by one run of the app is still there after a "restart" (new Services on the same file database). */
class PersistenceTest {

    @AfterEach
    void tearDown() {
        Session.logout();
    }

    private static Services open(Path dir) {
        Properties p = new Properties();
        String dbPath = dir.resolve("cafe").toAbsolutePath().toString().replace('\\', '/');
        p.setProperty("db.url", "jdbc:h2:" + dbPath
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1");
        AppConfig cfg = new AppConfig(p);
        return new Services(cfg, Database.fromConfig(cfg));
    }

    @Test
    void dataSurvivesRestartAndExistingDatabaseIsNotReinitialised(@TempDir Path dir) {
        Services first = open(dir);
        assertTrue(first.db.initializeIfEmpty(true));
        first.db.migrate();
        assertEquals(1, first.db.read(c -> Jdbc.count(c,
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE LOWER(TABLE_NAME)='nhat_ky'")).intValue(),
                "the complete schema survives closing the initialization connection");
        TestSupport.createAccounts(first);
        assertAuditTable(first, "after accounts");
        TestSupport.loginStaff(first);
        assertAuditTable(first, "after staff login");
        int hd = first.orders.openTableOrder(1);
        first.orders.addItem(hd, 2, 2, 2, Map.of(), null);
        first.orders.pay(new OrderService.PaymentRequest(hd, null, null, PhuongThucThanhToan.THE, null));
        assertAuditTable(first, "after payment");
        int open = first.orders.openTableOrder(2);
        assertAuditTable(first, "after open order");
        Session.logout();

        Services second = open(dir);
        assertFalse(second.db.initializeIfEmpty(true), "existing data must never be re-initialised");
        second.db.migrate();
        assertFalse(second.auth.needsInitialSetup());
        TestSupport.loginStaff(second);
        HoaDon paid = second.orders.getInvoice(hd).hoaDon();
        assertEquals(TrangThaiHoaDon.PAID, paid.trangThai());
        assertEquals(0, paid.tongTien().compareTo(new BigDecimal("62640")));   // 58,000 + 8% VAT
        assertEquals(TrangThaiHoaDon.OPEN, second.orders.getInvoice(open).hoaDon().trangThai());
    }

    private static void assertAuditTable(Services services, String stage) {
        var names = services.db.read(c -> Jdbc.list(c, "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES",
                rs -> rs.getString(1)));
        org.junit.jupiter.api.Assertions.assertTrue(names.stream().anyMatch("nhat_ky"::equalsIgnoreCase), stage + ": " + names);
        org.junit.jupiter.api.Assertions.assertTrue(names.stream().anyMatch("ngan_ke_toan"::equalsIgnoreCase), stage + ": " + names);
    }
}
