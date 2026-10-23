package cafe;

import cafe.controller.AppController;
import cafe.model.MenuItem;
import cafe.service.OrderService;
import cafe.service.Services;
import cafe.model.PhuongThucThanhToan;
import cafe.util.Session;
import cafe.view.LoginFrame;
import cafe.view.MainFrame;
import cafe.view.common.Theme;
import cafe.view.sales.AddItemDialog;
import cafe.view.sales.PaymentDialog;

import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JTabbedPane;
import javax.swing.RootPaneContainer;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Dimension;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Map;

/**
 * Developer tool (not part of the app): renders the main screens to PNG files without showing windows,
 * using an in-memory demo database. Run with an optional WIDTHxHEIGHT argument:
 * mvn test-compile exec:java -Dmain.class=cafe.ScreenshotTool -Dexec.classpathScope=test -Dexec.args="OUT_DIR 1366x800"
 */
public final class ScreenshotTool {

    private static File out;
    private static Dimension targetSize = new Dimension(1366, 800);

    public static void main(String[] args) {
        try {
            run(args);
            System.exit(0);
        } catch (Throwable e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void run(String[] args) throws Exception {
        out = new File(args.length > 0 ? args[0] : "screenshots");
        if (args.length > 1) {
            String[] parts = args[1].toLowerCase(java.util.Locale.ROOT).split("x", 2);
            if (parts.length != 2) throw new IllegalArgumentException("Resolution must be WIDTHxHEIGHT, e.g. 1366x800");
            targetSize = new Dimension(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        }
        out.mkdirs();
        SwingUtilities.invokeAndWait(Theme::install);
        Services s = TestSupport.newServices();
        TestSupport.createAccounts(s);
        TestSupport.loginManager(s);
        // some activity so the screens have content
        int a = s.orders.openTableOrder(3);
        s.orders.addItem(a, 2, 2, 2, Map.of(4, 1), "ít đá");
        s.orders.addItem(a, 3, 3, 1, Map.of(), null);
        int b = s.orders.openTableOrder(6);
        s.orders.addItem(b, 6, 2, 1, Map.of(2, 1), null);
        s.orders.pay(new OrderService.PaymentRequest(b, null, null, PhuongThucThanhToan.TIEN_MAT, new java.math.BigDecimal("100000")));
        int c = s.orders.openTableOrder(1);
        s.orders.addItem(c, 1, 1, 1, Map.of(), null);
        s.customers.createReservation(null, "Trần Thị Bình", "0912345678", 2, java.time.LocalDateTime.now().plusMinutes(45), 2, "Gần cửa sổ");

        Window[] login = new Window[1];
        SwingUtilities.invokeAndWait(() -> login[0] = new LoginFrame(new AppController(s.config), s.config.get("shop.name")));
        snap(login[0], "01-login");

        MainFrame[] frame = new MainFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            frame[0] = new MainFrame(new AppController(s.config), s);
            frame[0].pack();
            frame[0].setSize(targetSize);
            frame[0].validate();
        });
        Thread.sleep(1200);
        snap(frame[0], "02-dashboard");
        String[][] modules = {{"Bán hàng", "03-sales"}, {"Hóa đơn", "04-invoices"}, {"Khách hàng & đặt bàn", "05-customers"},
                {"Thực đơn & giá", "06-catalog"}, {"Bàn & khu vực", "07-tables"}, {"Khuyến mãi & thuế", "08-promotions"},
                {"Kho hàng", "09-inventory"}, {"Nhân viên", "10-employees"}, {"Báo cáo", "11-reports"},
                {"Nhật ký hệ thống", "12-audit"}, {"Ca thu ngân", "13-cash-shift"}};
        for (String[] m : modules) {
            click(frame[0], m[0]);
            Thread.sleep(900);
            if (m[0].equals("Bán hàng")) {
                click(frame[0], "Bàn 03");
                Thread.sleep(900);
            }
            snap(frame[0], m[1]);
        }
        click(frame[0], "Thực đơn & giá");
        selectTab(frame[0], "Bảng giá");
        Thread.sleep(900);
        SwingUtilities.invokeAndWait(() -> {
            javax.swing.JTable t = find(frame[0], javax.swing.JTable.class, 0);
            if (t != null && t.getRowCount() > 0) {
                t.setRowSelectionInterval(0, 0);
            }
        });
        Thread.sleep(900);
        snap(frame[0], "14-price-list");
        click(frame[0], "Kho hàng");
        selectTab(frame[0], "Lịch sử kho");
        Thread.sleep(1200);
        snap(frame[0], "15-stock-history");

        MenuItem item = s.catalog.findMenu().stream().filter(x -> x.mon().maMon() == 2).findFirst().orElseThrow();
        Window[] dlg = new Window[1];
        SwingUtilities.invokeAndWait(() -> dlg[0] = new AddItemDialog(frame[0], item, s.catalog.findToppings(true), null));
        snap(dlg[0], "16-add-item");
        SwingUtilities.invokeAndWait(() -> dlg[0] = new PaymentDialog(frame[0], s, s.orders.getInvoice(a)));
        Thread.sleep(1200);
        snap(dlg[0], "17-payment");

        Session.logout();
        TestSupport.loginStaff(s);
        SwingUtilities.invokeAndWait(() -> {
            frame[0] = new MainFrame(new AppController(s.config), s);
            frame[0].pack();
            frame[0].setSize(targetSize);
            frame[0].validate();
        });
        Thread.sleep(1200);
        snap(frame[0], "18-staff-dashboard");
        System.out.println("Screenshots written to " + out.getAbsolutePath());
    }

    private static void click(Container root, String text) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AbstractButton b = findButton(root, text);
            if (b == null) {
                throw new IllegalStateException("No button " + text);
            }
            b.doClick();
        });
    }

    private static void selectTab(Container root, String title) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (int i = 0; ; i++) {
                JTabbedPane tp = find(root, JTabbedPane.class, i);
                if (tp == null) {
                    return;
                }
                int idx = tp.indexOfTab(title);
                if (idx >= 0 && tp.isShowing() || idx >= 0 && tp.getParent().isVisible()) {
                    tp.setSelectedIndex(idx);
                    return;
                }
            }
        });
    }

    private static AbstractButton findButton(Container c, String text) {
        for (Component child : c.getComponents()) {
            if (child instanceof AbstractButton b && b.getText() != null && b.getText().contains(text) && b.isVisible()
                    && visibleChain(b)) {
                return b;
            }
            if (child instanceof Container k) {
                AbstractButton r = findButton(k, text);
                if (r != null) {
                    return r;
                }
            }
        }
        return null;
    }

    private static boolean visibleChain(Component c) {
        for (Component p = c; p != null && !(p instanceof Window); p = p.getParent()) {
            if (!p.isVisible()) {
                return false;
            }
        }
        return true;
    }

    /** n-th visible component of a type (depth first). */
    @SuppressWarnings("unchecked")
    private static <T> T find(Container c, Class<T> type, int n) {
        int[] count = {n};
        return (T) find(c, type, count);
    }

    private static Object find(Container c, Class<?> type, int[] count) {
        for (Component child : c.getComponents()) {
            if (!visibleChain(child)) {
                continue;
            }
            if (type.isInstance(child) && count[0]-- == 0) {
                return child;
            }
            if (child instanceof Container k) {
                Object r = find(k, type, count);
                if (r != null) {
                    return r;
                }
            }
        }
        return null;
    }

    private static void snap(Window w, String name) throws Exception {
        System.out.println("snap " + name);
        SwingUtilities.invokeAndWait(() -> {
            if (!w.isDisplayable()) {
                w.pack();
            }
            w.validate();
            Container root = ((RootPaneContainer) w).getRootPane();
            BufferedImage img = new BufferedImage(root.getWidth(), root.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
            root.printAll(g);
            g.dispose();
            try {
                ImageIO.write(img, "png", new File(out, name + ".png"));
            } catch (java.io.IOException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
