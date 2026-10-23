package cafe.view.sales;

import cafe.model.ChiTietHoaDon;
import cafe.model.HoaDon;
import cafe.model.InvoiceDetail;
import cafe.model.LoaiDon;
import cafe.model.LoaiMon;
import cafe.model.MenuItem;
import cafe.model.TableStatus;
import cafe.model.Topping;
import cafe.model.TrangThaiBan;
import cafe.model.TrangThaiHoaDon;
import cafe.service.Services;
import cafe.util.Dates;
import cafe.util.Money;
import cafe.view.common.Column;
import cafe.view.common.DataTable;
import cafe.view.common.Dialogs;
import cafe.view.common.Fields;
import cafe.view.common.Refreshable;
import cafe.view.common.Theme;
import cafe.view.common.UiTask;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JToggleButton;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.CardLayout;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Point of sale: table map (left), menu (centre) and the current order (right).
 * Every change is saved immediately through OrderService, so an open order survives a restart.
 */
public class SalesPanel extends JPanel implements Refreshable {

    private static final String ALL = "Tất cả";

    private final Services services;
    // left: tables and takeaway orders
    private final JPanel tableMap = new JPanel();
    private final DataTable<HoaDon> takeaway = new DataTable<>(
            Column.integer("HĐ", 50, HoaDon::maHD),
            Column.text("Loại", 80, HoaDon::loaiDon),
            Column.text("Mở lúc", 70, h -> h.ngayLap().toLocalTime().withNano(0).toString().substring(0, 5)),
            Column.text("Giao đến", 140, h -> h.diaChiGiao() == null ? "" : h.diaChiGiao()));
    // centre: menu
    private final JPanel productGrid = new JPanel(new GridLayout(0, 2, 10, 10));
    private final JPanel categoryBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
    private final JPanel productCards = new JPanel(new CardLayout());
    private final ButtonGroup categoryGroup = new ButtonGroup();
    private final Map<String, JToggleButton> categoryButtons = new LinkedHashMap<>();
    private final JLabel productEmpty = Theme.muted("Chưa có món nào có giá trong bảng giá hiện hành");
    private final JLabel orderPrompt = Theme.muted("Chọn bàn hoặc mở đơn mang đi trước khi thêm món");
    private List<MenuItem> fullMenu = List.of();
    private List<Topping> toppings = List.of();
    private String menuFilter = "";
    private String selectedCategory = ALL;
    // right: current order
    private final JLabel orderTitle = Theme.title("Chưa chọn đơn");
    private final JLabel orderInfo = Theme.muted("Chọn một bàn trên sơ đồ hoặc tạo đơn mang đi.");
    private final DataTable<ChiTietHoaDon> lines = new DataTable<>(
            Column.text("Món", 105, l -> l.tenMon() + " (" + l.tenSize() + ")"),
            Column.text("Topping", 95, l -> joinNonBlank(l.moTaTopping(), l.ghiChu())),
            Column.integer("SL", 35, ChiTietHoaDon::soLuong),
            Column.money("Thành tiền", 95, ChiTietHoaDon::tinhThanhTien));
    private final JLabel subtotal = new JLabel("0 đ");
    private final JButton[] orderButtons;
    private final JButton payButton = Theme.primary("Thanh toán");
    private InvoiceDetail current;

    public SalesPanel(Services services) {
        super(new BorderLayout());
        this.services = services;
        setBorder(Theme.pad(10, 10));

        // ----- left -----
        tableMap.setLayout(new BoxLayout(tableMap, BoxLayout.Y_AXIS));
        tableMap.setBackground(Theme.SURFACE);
        tableMap.setBorder(Theme.pad(8, 8));
        JScrollPane mapScroll = new JScrollPane(tableMap);
        Theme.styleScrollPane(mapScroll);
        mapScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        mapScroll.getVerticalScrollBar().setUnitIncrement(16);
        JButton newTakeaway = Theme.button("Đơn mang đi mới");
        newTakeaway.addActionListener(e -> openNonTable(LoaiDon.MANG_DI));
        JButton newDelivery = Theme.button("Đơn giao hàng mới");
        newDelivery.addActionListener(e -> openNonTable(LoaiDon.GIAO_HANG));
        JPanel takeawayPanel = new JPanel(new BorderLayout(0, 6));
        takeawayPanel.setBorder(Theme.pad(8, 8));
        JPanel tb = new JPanel(new GridLayout(1, 2, 6, 0));
        tb.setOpaque(false);
        tb.add(newTakeaway);
        tb.add(newDelivery);
        takeawayPanel.add(tb, BorderLayout.NORTH);
        takeaway.setEmptyText("Không có đơn mang đi / giao hàng đang mở");
        takeaway.onClick(h -> loadInvoice(h.maHD()));
        takeawayPanel.add(takeaway, BorderLayout.CENTER);
        JTabbedPane left = new JTabbedPane();
        left.addTab("Sơ đồ bàn", mapScroll);
        left.addTab("Mang đi / giao hàng", takeawayPanel);
        left.setPreferredSize(new Dimension(270, 0));

        // ----- centre -----
        JPanel menuPanel = new JPanel(new BorderLayout(0, 10));
        menuPanel.setBorder(Theme.pad(0, 12));
        JPanel menuTop = new JPanel(new BorderLayout(0, 8));
        menuTop.add(sectionLabel("Thực đơn"), BorderLayout.NORTH);
        javax.swing.JTextField searchMenu = Fields.search(t -> {
            menuFilter = t;
            applyMenuFilter();
        });
        searchMenu.putClientProperty("JTextField.placeholderText", "Tìm món hoặc nhóm món…");
        menuTop.add(searchMenu, BorderLayout.CENTER);
        JScrollPane categoryScroll = new JScrollPane(categoryBar,
                JScrollPane.VERTICAL_SCROLLBAR_NEVER, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        Theme.styleScrollPane(categoryScroll);
        categoryScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        categoryBar.setOpaque(false);
        categoryScroll.getViewport().setOpaque(false);
        menuTop.add(categoryScroll, BorderLayout.SOUTH);
        menuPanel.add(menuTop, BorderLayout.NORTH);

        productGrid.setOpaque(false);
        productGrid.setBorder(Theme.pad(2, 2));
        JScrollPane productScroll = new JScrollPane(productGrid);
        Theme.styleScrollPane(productScroll);
        productScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        productScroll.getViewport().setOpaque(false);
        productCards.setOpaque(false);
        productCards.add(productScroll, "products");
        JPanel empty = new JPanel(new BorderLayout());
        empty.setOpaque(false);
        empty.add(productEmpty, BorderLayout.CENTER);
        productCards.add(empty, "empty");
        menuPanel.add(productCards, BorderLayout.CENTER);
        JPanel addRow = new JPanel(new BorderLayout(8, 0));
        addRow.setOpaque(false);
        addRow.add(orderPrompt, BorderLayout.CENTER);
        menuPanel.add(addRow, BorderLayout.SOUTH);

        // ----- right -----
        JPanel order = Theme.card(new BorderLayout(0, 8));
        JPanel head = new JPanel(new BorderLayout(0, 2));
        head.setOpaque(false);
        head.add(orderTitle, BorderLayout.NORTH);
        head.add(orderInfo, BorderLayout.CENTER);
        order.add(head, BorderLayout.NORTH);
        lines.setEmptyText("Đơn chưa có món");
        lines.onDoubleClick(l -> editLine());
        order.add(lines, BorderLayout.CENTER);

        JButton plus = Theme.button("+1");
        plus.addActionListener(e -> changeQty(1));
        JButton minus = Theme.button("−1");
        minus.addActionListener(e -> changeQty(-1));
        JButton edit = Theme.button("Sửa");
        edit.addActionListener(e -> editLine());
        JButton remove = Theme.danger("Xóa món");
        remove.addActionListener(e -> removeLine());
        JButton transfer = Theme.button("Chuyển");
        transfer.setToolTipText("Chuyển bàn");
        transfer.addActionListener(e -> transferTable());
        JButton merge = Theme.button("Gộp đơn");
        merge.addActionListener(e -> mergeOrder());
        JButton cancel = Theme.danger("Hủy đơn");
        cancel.addActionListener(e -> cancelOrder());
        JButton printDraft = Theme.button("In tạm tính");
        printDraft.addActionListener(e -> {
            if (current != null) {
                ReceiptDialog.show(this, current, services.config);
            }
        });
        payButton.addActionListener(e -> pay());
        orderButtons = new JButton[]{plus, minus, edit, remove, transfer, merge, cancel, printDraft, payButton};
        lines.onSelect(line -> updateLineActions());

        JPanel bottom = new JPanel(new BorderLayout(0, 8));
        bottom.setOpaque(false);
        bottom.add(Fields.row(plus, minus, edit, remove), BorderLayout.NORTH);
        JPanel totalRow = new JPanel(new BorderLayout());
        totalRow.setOpaque(false);
        totalRow.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER));
        JLabel tl = new JLabel("Tạm tính");
        tl.setFont(Theme.BOLD);
        subtotal.setFont(Theme.font(Font.BOLD, 20));
        subtotal.setForeground(Theme.PRIMARY);
        totalRow.add(tl, BorderLayout.WEST);
        totalRow.add(subtotal, BorderLayout.EAST);
        bottom.add(totalRow, BorderLayout.CENTER);
        JPanel actions = new JPanel(new BorderLayout(0, 6));
        actions.setOpaque(false);
        JPanel secondaryActions = new JPanel(new GridLayout(1, 3, 6, 6));
        secondaryActions.setOpaque(false);
        secondaryActions.add(transfer);
        secondaryActions.add(merge);
        secondaryActions.add(cancel);
        JPanel checkoutActions = new JPanel(new GridLayout(1, 2, 6, 6));
        checkoutActions.setOpaque(false);
        checkoutActions.add(printDraft);
        payButton.setFont(Theme.font(Font.BOLD, 15));
        checkoutActions.add(payButton);
        actions.add(secondaryActions, BorderLayout.NORTH);
        actions.add(checkoutActions, BorderLayout.SOUTH);
        bottom.add(actions, BorderLayout.SOUTH);
        order.add(bottom, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, menuPanel, order);
        split.setResizeWeight(0.47);
        split.setBorder(null);
        split.setContinuousLayout(true);
        menuPanel.setMinimumSize(new Dimension(320, 0));
        order.setMinimumSize(new Dimension(350, 0));
        add(left, BorderLayout.WEST);
        add(split, BorderLayout.CENTER);
        showInvoice(null);

    }

    // other terminals may change table status: refresh the map while this screen is visible;
    // the timer lives only while the panel is in a window, so it stops after logout
    private final Timer autoRefresh = new Timer(30_000, e -> {
        if (isShowing()) {
            loadTables();
        }
    });

    @Override
    public void addNotify() {
        super.addNotify();
        autoRefresh.start();
    }

    @Override
    public void removeNotify() {
        autoRefresh.stop();
        super.removeNotify();
    }

    private static JLabel sectionLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.font(Font.BOLD, 15));
        return l;
    }

    private static String joinNonBlank(String a, String b) {
        if (a == null || a.isBlank()) {
            return b == null ? "" : b;
        }
        return b == null || b.isBlank() ? a : a + " | " + b;
    }

    private static String compactThousands(BigDecimal amount) {
        return amount.divide(BigDecimal.valueOf(1_000)).stripTrailingZeros().toPlainString().replace('.', ',') + "k";
    }

    @Override
    public void reload() {
        loadTables();
        UiTask.load(this, () -> new Object[]{services.catalog.findMenu(), services.catalog.findToppings(true), services.catalog.findLoaiMon()},
                r -> {
                    @SuppressWarnings("unchecked") List<MenuItem> m = (List<MenuItem>) r[0];
                    @SuppressWarnings("unchecked") List<Topping> t = (List<Topping>) r[1];
                    @SuppressWarnings("unchecked") List<LoaiMon> cats = (List<LoaiMon>) r[2];
                    fullMenu = m;
                    toppings = t;
                    createCategoryButtons(cats);
                    applyMenuFilter();
                });
        if (current != null) {
            loadInvoice(current.hoaDon().maHD());
        }
    }

    private void applyMenuFilter() {
        String foldedFilter = cafe.util.Text.fold(menuFilter).trim();
        List<MenuItem> filtered = fullMenu.stream()
                .filter(m -> selectedCategory.equals(ALL) || m.mon().tenLoai().equals(selectedCategory))
                .filter(m -> foldedFilter.isEmpty()
                        || cafe.util.Text.fold(m.mon().tenMon() + " " + m.mon().tenLoai()).contains(foldedFilter))
                .toList();
        productGrid.removeAll();
        filtered.forEach(item -> productGrid.add(productButton(item)));
        ((CardLayout) productCards.getLayout()).show(productCards, filtered.isEmpty() ? "empty" : "products");
        productEmpty.setText(filtered.isEmpty() && !fullMenu.isEmpty()
                ? "Không tìm thấy món phù hợp. Hãy đổi từ khóa hoặc nhóm món."
                : "Chưa có món nào có giá trong bảng giá hiện hành.");
        productGrid.revalidate();
        productGrid.repaint();
    }

    private void createCategoryButtons(List<LoaiMon> categories) {
        String previous = selectedCategory;
        categoryButtons.clear();
        categoryGroup.clearSelection();
        categoryBar.removeAll();
        addCategoryButton(ALL);
        categories.forEach(c -> addCategoryButton(c.tenLoai()));
        if (!categoryButtons.containsKey(previous)) previous = ALL;
        selectedCategory = previous;
        categoryButtons.get(previous).setSelected(true);
        categoryBar.revalidate();
        categoryBar.repaint();
    }

    private void addCategoryButton(String label) {
        JToggleButton b = new JToggleButton(label);
        b.setFont(Theme.font(Font.BOLD, 12));
        b.putClientProperty("JButton.buttonType", "roundRect");
        b.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        b.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        categoryGroup.add(b);
        categoryButtons.put(label, b);
        categoryBar.add(b);
        b.addActionListener(e -> {
            selectedCategory = label;
            applyMenuFilter();
        });
    }

    private JButton productButton(MenuItem item) {
        String prices = item.gia().stream().map(p -> Money.format(p.giaBan()) + " đ").collect(Collectors.joining(" · "));
        String startingPrice = item.gia().stream().map(p -> p.giaBan()).min(BigDecimal::compareTo)
                .map(SalesPanel::compactThousands).orElse("—");
        String html = "<html><div style='width:105px'><span style='color:#747984;font-size:10px'>"
                + escapeHtml(item.mon().tenLoai()) + "</span><br><b><span style='font-size:12px'>"
                + escapeHtml(item.mon().tenMon()) + "</span></b><br><span style='color:#B95F37;font-size:10px'><b>Từ "
                + startingPrice + " · " + item.gia().size() + " size</b></span></div></html>";
        JButton b = Theme.productTile(item.mon().tenMon());
        b.setPreferredSize(new Dimension(130, 86));
        b.setMinimumSize(new Dimension(0, 86));
        b.setText(html);
        b.setToolTipText("Thêm " + item.mon().tenMon() + " vào đơn · " + prices);
        b.addActionListener(e -> addItem(item));
        return b;
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    // ---------- table map ----------
    private void loadTables() {
        UiTask.load(this, () -> new Object[]{services.tables.findTableStatuses(), services.orders.findOpenNonTableOrders()}, r -> {
            @SuppressWarnings("unchecked") List<TableStatus> statuses = (List<TableStatus>) r[0];
            @SuppressWarnings("unchecked") List<HoaDon> open = (List<HoaDon>) r[1];
            renderTables(statuses);
            takeaway.setRows(open);
        });
    }

    private void renderTables(List<TableStatus> statuses) {
        tableMap.removeAll();
        Map<String, List<TableStatus>> byArea = new LinkedHashMap<>();
        for (TableStatus t : statuses) {
            byArea.computeIfAbsent(t.ban().tenKhuVuc(), k -> new java.util.ArrayList<>()).add(t);
        }
        JPanel legend = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        legend.setOpaque(false);
        for (TrangThaiBan st : new TrangThaiBan[]{TrangThaiBan.TRONG, TrangThaiBan.CO_KHACH, TrangThaiBan.DA_DAT}) {
            JLabel l = new JLabel(" " + st + " ");
            l.setOpaque(true);
            l.setBackground(tileColor(st));
            l.setForeground(tileText(st));
            l.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
            legend.add(l);
        }
        legend.setAlignmentX(LEFT_ALIGNMENT);
        legend.setMaximumSize(new Dimension(Integer.MAX_VALUE, legend.getPreferredSize().height));
        tableMap.add(legend);
        byArea.forEach((area, list) -> {
            JLabel title = new JLabel(area);
            title.setFont(Theme.BOLD);
            title.setBorder(BorderFactory.createEmptyBorder(12, 2, 6, 0));
            title.setAlignmentX(LEFT_ALIGNMENT);
            tableMap.add(title);
            JPanel grid = new JPanel(new GridLayout(0, 2, 6, 6));
            grid.setOpaque(false);
            grid.setAlignmentX(LEFT_ALIGNMENT);
            for (TableStatus t : list) {
                grid.add(tile(t));
            }
            grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, grid.getPreferredSize().height));
            tableMap.add(grid);
        });
        tableMap.add(javax.swing.Box.createVerticalGlue());
        tableMap.revalidate();
        tableMap.repaint();
    }

    private static Color tileColor(TrangThaiBan st) {
        return switch (st) {
            case TRONG -> Theme.SURFACE;
            case CO_KHACH -> Theme.PRIMARY;
            case DA_DAT -> Theme.WARNING_BG;
            case NGUNG -> new Color(0xEEEAE6);
        };
    }

    private static Color tileText(TrangThaiBan st) {
        return switch (st) {
            case TRONG -> Theme.SUCCESS;
            case CO_KHACH -> Color.WHITE;
            case DA_DAT -> Theme.WARNING_TEXT;
            case NGUNG -> Theme.MUTED;
        };
    }

    /** Status is written as text on every tile, so colour is never the only cue. */
    private Component tile(TableStatus t) {
        String sub = switch (t.trangThai()) {
            case CO_KHACH -> "Có khách · HĐ " + t.maHoaDonMo();
            case DA_DAT -> t.ghiChu().split(" - ")[0];
            case NGUNG -> "Ngừng phục vụ";
            default -> "Trống · " + t.ban().soCho() + " chỗ";
        };
        JButton b = new JButton("<html><center><b>" + t.ban().tenBan() + "</b><br><small>" + sub + "</small></center></html>");
        b.setPreferredSize(new Dimension(86, 68));
        b.setFocusPainted(false);
        b.setBackground(tileColor(t.trangThai()));
        b.setForeground(t.trangThai() == TrangThaiBan.TRONG ? Theme.TEXT : tileText(t.trangThai()));
        boolean selected = current != null && Objects.equals(current.hoaDon().maBan(), t.ban().maBan())
                && current.hoaDon().trangThai() == TrangThaiHoaDon.OPEN;
        b.setBorder(selected ? BorderFactory.createLineBorder(Theme.ACCENT, 3) : BorderFactory.createLineBorder(Theme.BORDER));
        b.setToolTipText(t.ban().tenBan() + " - " + t.trangThai() + (t.ghiChu() != null ? " - " + t.ghiChu() : ""));
        b.setEnabled(t.trangThai() != TrangThaiBan.NGUNG);
        b.addActionListener(e -> onTableClicked(t));
        return b;
    }

    private void onTableClicked(TableStatus t) {
        if (t.trangThai() == TrangThaiBan.CO_KHACH) {
            loadInvoice(t.maHoaDonMo());
            return;
        }
        String msg = t.trangThai() == TrangThaiBan.DA_DAT
                ? t.ban().tenBan() + " đã được đặt (" + t.ghiChu() + ").\nVẫn mở bàn cho khách? (Khách đặt bàn: dùng \"Nhận bàn\" ở màn hình Đặt bàn)"
                : "Mở " + t.ban().tenBan() + " cho khách mới?";
        if (!Dialogs.confirm(this, msg)) {
            return;
        }
        UiTask.load(this, () -> services.orders.openTableOrder(t.ban().maBan()), id -> {
            loadInvoice(id);
            loadTables();
        });
    }

    private void openNonTable(LoaiDon loai) {
        String address = null;
        if (loai == LoaiDon.GIAO_HANG) {
            address = JOptionPane.showInputDialog(this, "Địa chỉ giao hàng:", "Đơn giao hàng mới", JOptionPane.QUESTION_MESSAGE);
            if (address == null) {
                return;
            }
        }
        String addr = address;
        UiTask.load(this, () -> services.orders.openOrder(loai, addr), id -> {
            loadInvoice(id);
            loadTables();
        });
    }

    // ---------- current order ----------
    private void loadInvoice(int maHD) {
        UiTask.load(this, () -> services.orders.getInvoice(maHD), d -> {
            showInvoice(d.hoaDon().trangThai() == TrangThaiHoaDon.OPEN ? d : null);
            loadTables();
        });
    }

    private void showInvoice(InvoiceDetail d) {
        current = d;
        boolean open = d != null;
        for (JButton b : orderButtons) {
            b.setEnabled(open);
        }
        if (!open) {
            orderTitle.setText("Chưa chọn đơn");
            orderInfo.setText("Chọn một bàn trên sơ đồ hoặc tạo đơn mang đi.");
            lines.setRows(List.of());
            subtotal.setText("0 đ");
            orderPrompt.setText("Chọn bàn hoặc mở đơn mang đi trước khi thêm món");
            return;
        }
        HoaDon h = d.hoaDon();
        orderTitle.setText(h.nhan());
        orderInfo.setText("Mở lúc " + Dates.format(h.ngayLap()) + " · " + h.tenNV()
                + (h.diaChiGiao() != null ? " · Giao: " + h.diaChiGiao() : ""));
        lines.setRows(d.chiTiet());
        orderPrompt.setText("Đang phục vụ · chạm vào món để chọn size và topping");
        subtotal.setText(Money.format(d.tamTinh()));
        boolean dineIn = h.loaiDon() == LoaiDon.TAI_BAN;
        orderButtons[4].setEnabled(dineIn);   // transfer
        orderButtons[5].setEnabled(true);     // merge
        orderButtons[6].setEnabled(true);     // cancel
        orderButtons[7].setEnabled(true);     // print draft
        payButton.setEnabled(!d.chiTiet().isEmpty());
        updateLineActions();
    }

    private void updateLineActions() {
        boolean enabled = current != null && lines.getSelected() != null;
        for (int i = 0; i < 4; i++) {
            orderButtons[i].setEnabled(enabled);
        }
        if (current != null && current.hoaDon().loaiDon() != LoaiDon.TAI_BAN) orderButtons[4].setEnabled(false);
        if (current != null) payButton.setEnabled(!current.chiTiet().isEmpty());
    }

    private boolean requireOrder() {
        if (current == null) {
            Dialogs.info(this, "Hãy chọn bàn hoặc tạo đơn mang đi trước.");
            return false;
        }
        return true;
    }

    private void addItem(MenuItem item) {
        if (!requireOrder()) {
            return;
        }
        AddItemDialog.Choice c = new AddItemDialog(this, item, toppings, null).ask();
        if (c == null) {
            return;
        }
        int maHD = current.hoaDon().maHD();
        UiTask.load(this, () -> services.orders.addItem(maHD, item.mon().maMon(), c.maSize(), c.soLuong(), c.toppings(), c.ghiChu()),
                id -> loadInvoice(maHD));
    }

    private ChiTietHoaDon selectedLine() {
        ChiTietHoaDon l = lines.getSelected();
        if (l == null) {
            Dialogs.info(this, "Hãy chọn một món trong đơn.");
        }
        return l;
    }

    private void changeQty(int delta) {
        ChiTietHoaDon l = selectedLine();
        if (l == null) {
            return;
        }
        int q = l.soLuong() + delta;
        if (q <= 0 && !Dialogs.confirm(this, "Xóa \"" + l.tenMon() + "\" khỏi đơn?")) {
            return;
        }
        int maHD = current.hoaDon().maHD();
        UiTask.exec(this, () -> services.orders.capNhatSoLuong(l.maCTHD(), q), () -> loadInvoice(maHD));
    }

    private void editLine() {
        ChiTietHoaDon l = selectedLine();
        if (l == null) {
            return;
        }
        MenuItem item = fullMenu.stream().filter(m -> m.mon().maMon() == l.maMon()).findFirst().orElse(null);
        if (item == null) {
            Dialogs.info(this, "Món này hiện không còn trong thực đơn, chỉ có thể đổi số lượng hoặc xóa.");
            return;
        }
        AddItemDialog.Choice c = new AddItemDialog(this, item, toppings, l).ask();
        if (c == null) {
            return;
        }
        int maHD = current.hoaDon().maHD();
        UiTask.exec(this, () -> services.orders.updateLine(l.maCTHD(), c.soLuong(), c.toppings(), c.ghiChu()), () -> loadInvoice(maHD));
    }

    private void removeLine() {
        ChiTietHoaDon l = selectedLine();
        if (l == null || !Dialogs.confirm(this, "Xóa \"" + l.tenMon() + " (" + l.tenSize() + ")\" khỏi đơn?")) {
            return;
        }
        int maHD = current.hoaDon().maHD();
        UiTask.exec(this, () -> services.orders.removeLine(l.maCTHD()), () -> loadInvoice(maHD));
    }

    private void transferTable() {
        if (!requireOrder()) {
            return;
        }
        int maHD = current.hoaDon().maHD();
        UiTask.load(this, services.tables::findTableStatuses, statuses -> {
            List<TableStatus> free = statuses.stream().filter(s -> s.trangThai() == TrangThaiBan.TRONG || s.trangThai() == TrangThaiBan.DA_DAT).toList();
            if (free.isEmpty()) {
                Dialogs.info(this, "Không còn bàn trống.");
                return;
            }
            Object[] options = free.stream().map(s -> s.ban().tenBan() + " (" + s.ban().tenKhuVuc() + ")").toArray();
            Object pick = JOptionPane.showInputDialog(this, "Chuyển " + current.hoaDon().nhan() + " sang bàn:", "Chuyển bàn",
                    JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
            if (pick == null) {
                return;
            }
            int target = free.get(java.util.Arrays.asList(options).indexOf(pick)).ban().maBan();
            UiTask.exec(this, () -> services.orders.transferTable(maHD, target), () -> loadInvoice(maHD));
        });
    }

    private void mergeOrder() {
        if (!requireOrder()) {
            return;
        }
        int into = current.hoaDon().maHD();
        UiTask.load(this, services.orders::findOpenOrders, open -> {
            List<HoaDon> others = open.stream().filter(h -> h.maHD() != into).toList();
            if (others.isEmpty()) {
                Dialogs.info(this, "Không có đơn đang mở nào khác để gộp.");
                return;
            }
            Object[] options = others.stream().map(HoaDon::nhan).toArray();
            Object pick = JOptionPane.showInputDialog(this, "Gộp đơn nào VÀO " + current.hoaDon().nhan() + "?\n(Đơn được chọn sẽ được đóng lại)",
                    "Gộp đơn", JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
            if (pick == null) {
                return;
            }
            int from = others.get(java.util.Arrays.asList(options).indexOf(pick)).maHD();
            UiTask.exec(this, () -> services.orders.mergeOrders(from, into), () -> loadInvoice(into));
        });
    }

    private void cancelOrder() {
        if (!requireOrder()) {
            return;
        }
        HoaDon h = current.hoaDon();
        String reason = Dialogs.askReason(this, "Hủy đơn", "Lý do hủy " + h.nhan() + ":");
        if (reason == null) {
            return;
        }
        UiTask.exec(this, () -> services.orders.cancelOrder(h.maHD(), reason), () -> {
            showInvoice(null);
            loadTables();
        });
    }

    private void pay() {
        if (!requireOrder()) {
            return;
        }
        if (current.chiTiet().isEmpty()) {
            Dialogs.info(this, "Đơn chưa có món nào.");
            return;
        }
        InvoiceDetail paid = new PaymentDialog(this, services, current).ask();
        if (paid == null) {
            return;
        }
        showInvoice(null);
        loadTables();
        ReceiptDialog.show(this, paid, services.config);
    }
}
