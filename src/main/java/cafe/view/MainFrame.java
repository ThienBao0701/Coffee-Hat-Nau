package cafe.view;

import cafe.controller.AppController;
import cafe.model.CashShift;
import cafe.model.NhanVien;
import cafe.service.Services;
import cafe.util.Permission;
import cafe.util.Session;
import cafe.view.admin.AuditPanel;
import cafe.view.admin.CashShiftPanel;
import cafe.view.admin.EmployeePanel;
import cafe.view.admin.ReportPanel;
import cafe.view.catalog.CatalogPanel;
import cafe.view.catalog.PromotionPanel;
import cafe.view.catalog.TableAdminPanel;
import cafe.view.common.Refreshable;
import cafe.view.common.NavIcon;
import cafe.view.common.Theme;
import cafe.view.common.UiTask;
import cafe.view.inventory.InventoryPanel;
import cafe.view.sales.CustomerPanel;
import cafe.view.sales.InvoicePanel;
import cafe.view.sales.SalesPanel;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Main application shell: graphite navigation, bright workspace and compact utility bar. */
public class MainFrame extends JFrame {

    private final Services services;
    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final JPanel nav = new JPanel();
    private final ButtonGroup navGroup = new ButtonGroup();
    private final Map<String, Supplier<JComponent>> factories = new HashMap<>();
    private final Map<String, JComponent> created = new HashMap<>();
    private final Map<String, JToggleButton> navButtons = new HashMap<>();
    private final JLabel moduleTitle = new JLabel("Tổng quan");
    private final JLabel status = Theme.muted(" ");
    private String lastNavigationGroup;
    private Timer clockTimer;
    private Timer shiftTimer;

    public MainFrame(AppController controller, Services services) {
        this.services = services;
        NhanVien me = Session.current();
        setTitle(services.config.get("shop.name") + " - Quản lý quán cà phê");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1060, 650));

        getContentPane().add(header(controller, me), BorderLayout.NORTH);
        getContentPane().add(sidebar(me), BorderLayout.WEST);

        addModule("Hoạt động", "Tổng quan", null, () -> new DashboardPanel(services));
        addModule("Hoạt động", "Bán hàng", Permission.SELL, () -> new SalesPanel(services));
        addModule("Hoạt động", "Ca thu ngân", Permission.CASHIER_SHIFT, () -> new CashShiftPanel(services));
        addModule("Hoạt động", "Hóa đơn", Permission.VIEW_OWN_INVOICES, () -> new InvoicePanel(services));
        addModule("Khách hàng", "Khách hàng & đặt bàn", Permission.MANAGE_CUSTOMERS, () -> new CustomerPanel(services));
        addModule("Khách hàng", "Bàn & khu vực", Permission.MANAGE_TABLES, () -> new TableAdminPanel(services));
        addModule("Thực đơn", "Thực đơn & giá", Permission.MANAGE_CATALOG, () -> new CatalogPanel(services));
        addModule("Thực đơn", "Khuyến mãi & thuế", Permission.MANAGE_PROMOTIONS, () -> new PromotionPanel(services));
        addModule("Kho", "Kho hàng", Permission.MANAGE_INVENTORY, () -> new InventoryPanel(services));
        addModule("Quản trị", "Nhân viên", Permission.MANAGE_EMPLOYEES, () -> new EmployeePanel(services));
        addModule("Quản trị", "Báo cáo", Permission.VIEW_REPORTS, () -> new ReportPanel(services));
        addModule("Quản trị", "Nhật ký hệ thống", Permission.VIEW_AUDIT_LOG, () -> new AuditPanel(services));

        content.setBackground(Theme.BG);
        getContentPane().add(content, BorderLayout.CENTER);
        getContentPane().add(statusBar(), BorderLayout.SOUTH);

        show("Tổng quan");
        setSize(1280, 800);
        setLocationRelativeTo(null);
        setExtendedState(MAXIMIZED_BOTH);
    }

    private JPanel sidebar(NhanVien me) {
        JPanel shell = new JPanel(new BorderLayout());
        shell.setBackground(Theme.DARK);
        shell.setPreferredSize(new Dimension(248, 0));
        shell.add(brand(), BorderLayout.NORTH);

        nav.setLayout(new BoxLayout(nav, BoxLayout.Y_AXIS));
        nav.setBackground(Theme.DARK);
        nav.setBorder(BorderFactory.createEmptyBorder(8, 0, 12, 0));
        JScrollPane navScroll = new JScrollPane(nav);
        Theme.styleScrollPane(navScroll);
        navScroll.setBorder(BorderFactory.createEmptyBorder());
        navScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        navScroll.getViewport().setBackground(Theme.DARK);
        navScroll.getVerticalScrollBar().setUnitIncrement(20);
        shell.add(navScroll, BorderLayout.CENTER);
        shell.add(userFooter(me), BorderLayout.SOUTH);
        return shell;
    }

    private JPanel brand() {
        JPanel brand = new JPanel(new BorderLayout(12, 0));
        brand.setBackground(Theme.DARK);
        brand.setBorder(BorderFactory.createEmptyBorder(22, 18, 20, 16));
        JPanel mark = new JPanel(new GridLayout(1, 1));
        mark.setBackground(Theme.ACCENT);
        mark.setPreferredSize(new Dimension(42, 42));
        JLabel monogram = new JLabel("H", JLabel.CENTER);
        monogram.setForeground(Color.WHITE);
        monogram.setFont(Theme.font(Font.BOLD, 23));
        mark.add(monogram);
        JPanel wordmark = new JPanel();
        wordmark.setOpaque(false);
        wordmark.setLayout(new BoxLayout(wordmark, BoxLayout.Y_AXIS));
        JLabel name = new JLabel("CÀ PHÊ HẠT NÂU");
        name.setFont(Theme.font(Font.BOLD, 14));
        name.setForeground(Color.WHITE);
        JLabel descriptor = new JLabel("CAFE MANAGEMENT");
        descriptor.setFont(Theme.font(Font.BOLD, 10));
        descriptor.setForeground(new Color(0xA5ABB4));
        descriptor.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        wordmark.add(name);
        wordmark.add(descriptor);
        brand.add(mark, BorderLayout.WEST);
        brand.add(wordmark, BorderLayout.CENTER);
        return brand;
    }

    private JPanel userFooter(NhanVien me) {
        JPanel footer = new JPanel(new BorderLayout(10, 0));
        footer.setBackground(Theme.DARK);
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.DARK_2),
                BorderFactory.createEmptyBorder(14, 16, 16, 16)));
        JLabel initial = new JLabel(me.tenNV().isBlank() ? "?" : me.tenNV().substring(0, 1).toUpperCase(), JLabel.CENTER);
        initial.setOpaque(true);
        initial.setBackground(Theme.DARK_2);
        initial.setForeground(new Color(0xF0B08D));
        initial.setFont(Theme.font(Font.BOLD, 15));
        initial.setPreferredSize(new Dimension(34, 34));
        JPanel identity = new JPanel();
        identity.setOpaque(false);
        identity.setLayout(new BoxLayout(identity, BoxLayout.Y_AXIS));
        JLabel name = new JLabel(me.tenNV());
        name.setFont(Theme.BOLD);
        name.setForeground(Color.WHITE);
        JLabel role = new JLabel(String.valueOf(me.vaiTro()));
        role.setFont(Theme.SMALL);
        role.setForeground(new Color(0xA5ABB4));
        role.setBorder(BorderFactory.createEmptyBorder(3, 0, 0, 0));
        identity.add(name);
        identity.add(role);
        footer.add(initial, BorderLayout.WEST);
        footer.add(identity, BorderLayout.CENTER);
        return footer;
    }

    private JPanel header(AppController controller, NhanVien me) {
        JPanel h = new JPanel(new BorderLayout(20, 0));
        h.setBackground(Theme.SURFACE);
        h.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
                BorderFactory.createEmptyBorder(10, 24, 10, 22)));

        JPanel page = new JPanel();
        page.setOpaque(false);
        page.setLayout(new BoxLayout(page, BoxLayout.Y_AXIS));
        JLabel eyebrow = new JLabel("HẠT NÂU  /  WORKSPACE");
        eyebrow.setForeground(Theme.MUTED);
        eyebrow.setFont(Theme.font(Font.BOLD, 10));
        moduleTitle.setFont(Theme.font(Font.BOLD, 20));
        moduleTitle.setForeground(Theme.TEXT);
        page.add(eyebrow);
        page.add(Box.createVerticalStrut(2));
        page.add(moduleTitle);
        h.add(page, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 2));
        right.setOpaque(false);
        JPanel user = new JPanel();
        user.setOpaque(false);
        user.setLayout(new BoxLayout(user, BoxLayout.Y_AXIS));
        JLabel userName = new JLabel(me.tenNV());
        userName.setFont(Theme.font(Font.BOLD, 13));
        JLabel role = new JLabel(String.valueOf(me.vaiTro()));
        role.setForeground(Theme.MUTED);
        role.setFont(Theme.font(Font.PLAIN, 11));
        user.add(userName);
        user.add(role);

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM  HH:mm");
        JLabel clock = Theme.muted(fmt.format(LocalDateTime.now()));
        Runnable tick = () -> clock.setText(fmt.format(LocalDateTime.now()));
        clockTimer = new Timer(15_000, e -> tick.run());
        clockTimer.start();

        JButton shift = Theme.button("Ca: đang tải…");
        shift.setBackground(new Color(0xEAF2EC));
        shift.setForeground(Theme.SUCCESS);
        shift.setEnabled(Session.can(Permission.CASHIER_SHIFT));
        shift.setToolTipText("Mở màn hình quản lý ca thu ngân");
        shift.addActionListener(e -> {
            if (factories.containsKey("Ca thu ngân")) show("Ca thu ngân");
        });
        Runnable refreshShift = () -> UiTask.load(this, services.cashShifts::visibleActiveShift, active -> {
            if (active == null) shift.setText("Chưa mở ca");
            else shift.setText("Ca #" + active.maCa() + " · Đang mở");
        });
        refreshShift.run();
        shiftTimer = new Timer(30_000, e -> refreshShift.run());
        shiftTimer.start();

        JButton changePw = Theme.button("Đổi mật khẩu");
        changePw.addActionListener(e -> {
            if (ChangePasswordDialog.show(this, services, false)) {
                cafe.view.common.Dialogs.info(this, "Đã đổi mật khẩu.");
            }
        });
        JButton logout = Theme.button("Đăng xuất");
        logout.setForeground(Theme.DANGER);
        logout.addActionListener(e -> controller.logout());
        right.add(user);
        right.add(shift);
        right.add(clock);
        right.add(changePw);
        right.add(logout);
        h.add(right, BorderLayout.EAST);
        return h;
    }

    private JPanel statusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(Theme.SURFACE);
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER),
                BorderFactory.createEmptyBorder(5, 16, 5, 16)));
        String dbKind = services.config.get("db.url").startsWith("jdbc:mysql") ? "MySQL" : "H2 · nhúng";
        status.setText("Sẵn sàng  ·  Bán hàng và quản lý quán");
        bar.add(status, BorderLayout.WEST);
        bar.add(Theme.muted("Kết nối  " + dbKind), BorderLayout.EAST);
        return bar;
    }

    @Override
    public void dispose() {
        if (clockTimer != null) clockTimer.stop();
        if (shiftTimer != null) shiftTimer.stop();
        super.dispose();
    }

    /** Adds a navigation entry only if the role has the permission (services check again). */
    private void addModule(String group, String title, Permission required, Supplier<JComponent> factory) {
        if (required != null && !Session.can(required)) return;
        if (!group.equals(lastNavigationGroup)) {
            JLabel heading = new JLabel(group.toUpperCase(java.util.Locale.ROOT));
            heading.setForeground(new Color(0x89919C));
            heading.setFont(Theme.font(Font.BOLD, 10));
            heading.setBorder(BorderFactory.createEmptyBorder(13, 18, 6, 10));
            heading.setAlignmentX(Component.LEFT_ALIGNMENT);
            nav.add(heading);
            lastNavigationGroup = group;
        }
        factories.put(title, factory);
        JToggleButton b = new JToggleButton(title);
        b.setName(title);
        b.getAccessibleContext().setAccessibleName(title);
        b.setHorizontalAlignment(JToggleButton.LEFT);
        b.setIcon(new NavIcon(title));
        b.setIconTextGap(11);
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        b.setPreferredSize(new Dimension(220, 44));
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        b.setFocusPainted(true);
        b.setForeground(new Color(0xC6CBD2));
        b.setOpaque(true);
        b.setBackground(Theme.DARK);
        b.setBorder(BorderFactory.createEmptyBorder(10, 18, 10, 14));
        b.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        b.addChangeListener(e -> {
            if (b.isSelected()) {
                b.setBackground(Theme.DARK_HOVER);
                b.setForeground(Color.WHITE);
                b.setFont(Theme.BOLD);
                b.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 3, 0, 0, Theme.ACCENT),
                        BorderFactory.createEmptyBorder(10, 15, 10, 14)));
            } else {
                b.setBackground(Theme.DARK);
                b.setForeground(new Color(0xC6CBD2));
                b.setFont(Theme.BASE);
                b.setBorder(BorderFactory.createEmptyBorder(10, 18, 10, 14));
            }
        });
        b.addActionListener(e -> show(title));
        navGroup.add(b);
        navButtons.put(title, b);
        nav.add(b);
    }

    private void show(String title) {
        JToggleButton selected = navButtons.get(title);
        if (selected != null) selected.setSelected(true);
        JComponent panel = created.get(title);
        if (panel == null) {
            panel = factories.get(title).get();
            created.put(title, panel);
            content.add(panel, title);
        }
        cards.show(content, title);
        moduleTitle.setText(title);
        if (panel instanceof Refreshable r) r.reload();
    }
}
