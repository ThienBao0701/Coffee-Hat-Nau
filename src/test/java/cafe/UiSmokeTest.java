package cafe;

import cafe.service.Services;
import cafe.util.Session;
import cafe.view.DashboardPanel;
import cafe.view.admin.AuditPanel;
import cafe.view.admin.CashShiftPanel;
import cafe.view.admin.EmployeePanel;
import cafe.view.admin.ReportPanel;
import cafe.view.catalog.CatalogPanel;
import cafe.view.catalog.PromotionPanel;
import cafe.view.catalog.TableAdminPanel;
import cafe.view.common.Refreshable;
import cafe.view.inventory.InventoryPanel;
import cafe.view.sales.CustomerPanel;
import cafe.view.sales.InvoicePanel;
import cafe.view.sales.SalesPanel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.swing.JComponent;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Builds every screen (headless, no window) for each role and loads its data.
 * Any exception on the Swing thread - including an error dialog, which cannot open headless - fails the test.
 */
class UiSmokeTest {

    private final List<Throwable> edtErrors = Collections.synchronizedList(new ArrayList<>());

    @AfterEach
    void tearDown() {
        Session.logout();
    }

    @Test
    void managerScreensLoad() throws Exception {
        Services s = TestSupport.newServices();
        TestSupport.createAccounts(s);
        TestSupport.loginManager(s);
        load(s, List.of(DashboardPanel::new, SalesPanel::new, CashShiftPanel::new, InvoicePanel::new, CustomerPanel::new, CatalogPanel::new,
                TableAdminPanel::new, PromotionPanel::new, InventoryPanel::new, EmployeePanel::new, ReportPanel::new, AuditPanel::new));
    }

    @Test
    void employeeScreensLoad() throws Exception {
        Services s = TestSupport.newServices();
        TestSupport.createAccounts(s);
        TestSupport.loginStaff(s);
        load(s, List.of(DashboardPanel::new, SalesPanel::new, CashShiftPanel::new, InvoicePanel::new, CustomerPanel::new));
    }

    private void load(Services s, List<Function<Services, JComponent>> screens) throws Exception {
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        SwingUtilities.invokeAndWait(() -> Thread.currentThread().setUncaughtExceptionHandler((t, e) -> edtErrors.add(e)));
        try {
            for (Function<Services, JComponent> factory : screens) {
                List<Refreshable> toLoad = new ArrayList<>();
                SwingUtilities.invokeAndWait(() -> {
                    JComponent panel = factory.apply(s);
                    panel.setSize(1200, 800);
                    collectRefreshables(panel, toLoad);
                    toLoad.forEach(Refreshable::reload);
                });
                Thread.sleep(800);   // let background loads finish and post their results
                SwingUtilities.invokeAndWait(() -> { });
            }
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previous);
        }
        assertEquals(List.of(), edtErrors, "errors on the Swing thread");
    }

    /** The screen itself and every tab inside it (each tab loads lazily in the app). */
    private static void collectRefreshables(Component c, List<Refreshable> out) {
        if (c instanceof Refreshable r) {
            out.add(r);
        }
        if (c instanceof JTabbedPane tabs) {
            for (int i = 0; i < tabs.getTabCount(); i++) {
                collectRefreshables(tabs.getComponentAt(i), out);
            }
        } else if (c instanceof Container k) {
            for (Component child : k.getComponents()) {
                collectRefreshables(child, out);
            }
        }
    }
}
