package cafe.view.inventory;

import cafe.model.BienDongKho;
import cafe.model.ChiTietPhieuNhap;
import cafe.model.ChiTietPhieuXuat;
import cafe.model.CongThuc;
import cafe.model.Mon;
import cafe.model.NguyenLieu;
import cafe.model.NhaCungCap;
import cafe.model.PhieuNhap;
import cafe.model.PhieuXuat;
import cafe.model.TrangThaiPhieu;
import cafe.service.Services;
import cafe.util.Check;
import cafe.util.Dates;
import cafe.util.Money;
import cafe.view.common.Column;
import cafe.view.common.DataTable;
import cafe.view.common.Dialogs;
import cafe.view.common.Fields;
import cafe.view.common.FormDialog;
import cafe.view.common.ListPanel;
import cafe.view.common.Refreshable;
import cafe.view.common.Theme;
import cafe.view.common.UiTask;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Ingredients, suppliers, recipes, stock receipts/issues and movement history (manager). */
public class InventoryPanel extends JPanel implements Refreshable {

    private static final String ALL = "Tất cả nguyên liệu";

    private final Services services;
    private final JTabbedPane tabs = new JTabbedPane();
    private final ListPanel<NguyenLieu> ingredients;
    private final ListPanel<NhaCungCap> suppliers;
    private final RecipeTab recipes = new RecipeTab();
    private final ListPanel<PhieuNhap> receipts;
    private final ListPanel<PhieuXuat> issues;
    private final ListPanel<BienDongKho> movements;
    private final JTextField from = Fields.date(LocalDate.now().minusDays(30));
    private final JTextField to = Fields.date(LocalDate.now());
    private final JComboBox<Object> movementFilter = new JComboBox<>();

    public InventoryPanel(Services services) {
        super(new BorderLayout());
        this.services = services;

        ingredients = new ListPanel<>(services.inventory::findNguyenLieu, new DataTable<>(
                Column.integer("Mã", 50, NguyenLieu::maNL),
                Column.text("Nguyên liệu", 200, NguyenLieu::tenNL),
                Column.text("ĐVT", 70, NguyenLieu::donViTinh),
                Column.qty("Tồn kho", 100, NguyenLieu::soLuongTon),
                Column.qty("Mức cảnh báo", 100, NguyenLieu::mucCanhBao),
                Column.text("Tình trạng", 110, n -> !n.trangThai() ? "Ngừng dùng" : n.sapHet() ? "Sắp hết" : "Đủ dùng")));
        ingredients.table.setRowColor(n -> n.trangThai() && n.sapHet() ? Theme.WARNING_BG : null);
        ingredients.action(Theme.primary("Thêm nguyên liệu"), false, n -> editIngredient(null));
        ingredients.action(Theme.button("Sửa"), true, this::editIngredient);
        ingredients.action(Theme.button("Điều chỉnh tồn (kiểm kê)"), true, this::adjustStock);

        suppliers = new ListPanel<>(() -> services.inventory.findNhaCungCap(false), new DataTable<>(
                Column.text("Nhà cung cấp", 200, NhaCungCap::tenNCC),
                Column.text("Điện thoại", 110, n -> n.soDienThoai() == null ? "" : n.soDienThoai()),
                Column.text("Email", 160, n -> n.email() == null ? "" : n.email()),
                Column.text("Địa chỉ", 180, n -> n.diaChi() == null ? "" : n.diaChi()),
                Column.text("Ghi chú", 140, n -> n.ghiChu() == null ? "" : n.ghiChu()),
                Column.text("Trạng thái", 100, n -> n.trangThai() ? "Đang giao dịch" : "Ngừng")));
        suppliers.action(Theme.primary("Thêm nhà cung cấp"), false, n -> editSupplier(null));
        suppliers.action(Theme.button("Sửa"), true, this::editSupplier);
        suppliers.table.onDoubleClick(this::editSupplier);

        receipts = new ListPanel<>(() -> services.inventory.findPhieuNhap(range()[0], range()[1]), new DataTable<>(
                Column.integer("Mã PN", 60, PhieuNhap::maPN),
                Column.dateTime("Ngày nhập", 120, PhieuNhap::ngayNhap),
                Column.text("Nhà cung cấp", 180, PhieuNhap::tenNCC),
                Column.text("Người nhập", 120, PhieuNhap::tenNV),
                Column.money("Tổng tiền", 110, PhieuNhap::tongTien),
                Column.text("Trạng thái", 100, PhieuNhap::trangThai),
                Column.text("Ghi chú / lý do hủy", 180, p -> p.lyDoHuy() != null ? p.lyDoHuy() : p.ghiChu() == null ? "" : p.ghiChu())));
        receipts.action(Theme.primary("Tạo phiếu nhập"), false, p -> newVoucher(true));
        receipts.action(Theme.button("Xem chi tiết"), true, p -> showReceipt(p.maPN()));
        receipts.action(Theme.danger("Hủy phiếu"), true, p -> cancelVoucher(p.maPN(), true, p.trangThai()));
        receipts.table.onDoubleClick(p -> showReceipt(p.maPN()));

        issues = new ListPanel<>(() -> services.inventory.findPhieuXuat(range()[0], range()[1]), new DataTable<>(
                Column.integer("Mã PX", 60, PhieuXuat::maPX),
                Column.dateTime("Ngày xuất", 120, PhieuXuat::ngayXuat),
                Column.text("Lý do", 200, PhieuXuat::lyDo),
                Column.text("Người xuất", 120, PhieuXuat::tenNV),
                Column.money("Tổng tiền", 110, PhieuXuat::tongTien),
                Column.text("Trạng thái", 100, PhieuXuat::trangThai),
                Column.text("Lý do hủy", 160, p -> p.lyDoHuy() == null ? "" : p.lyDoHuy())));
        issues.action(Theme.primary("Tạo phiếu xuất"), false, p -> newVoucher(false));
        issues.action(Theme.button("Xem chi tiết"), true, p -> showIssue(p.maPX()));
        issues.action(Theme.danger("Hủy phiếu"), true, p -> cancelVoucher(p.maPX(), false, p.trangThai()));
        issues.table.onDoubleClick(p -> showIssue(p.maPX()));

        movementFilter.addItem(ALL);
        movements = new ListPanel<>(() -> services.inventory.findMovements(range()[0], range()[1],
                movementFilter.getSelectedItem() instanceof NguyenLieu n ? n.maNL() : null), new DataTable<>(
                Column.dateTime("Thời điểm", 120, BienDongKho::thoiDiem),
                Column.text("Nguyên liệu", 160, BienDongKho::tenNL),
                Column.text("Loại", 130, BienDongKho::loai),
                Column.qty("Số lượng (+/-)", 100, BienDongKho::soLuong),
                Column.qty("Tồn sau", 90, BienDongKho::tonSau),
                Column.text("Chứng từ", 80, b -> b.thamChieu() == null ? "" : b.thamChieu()),
                Column.text("Người thực hiện", 120, b -> b.tenNV() == null ? "" : b.tenNV()),
                Column.text("Ghi chú", 180, b -> b.ghiChu() == null ? "" : b.ghiChu())));
        JButton filter = Theme.button("Lọc");
        filter.addActionListener(e -> reload());
        from.setColumns(8);
        to.setColumns(8);
        JPanel range = Fields.row(new JLabel("Từ"), from, new JLabel("đến"), to, filter);
        movements.addFilter(Fields.row(movementFilter));

        tabs.addTab("Nguyên liệu", ingredients);
        tabs.addTab("Nhà cung cấp", suppliers);
        tabs.addTab("Công thức", recipes);
        tabs.addTab("Phiếu nhập", receipts);
        tabs.addTab("Phiếu xuất", issues);
        tabs.addTab("Lịch sử kho", movements);
        tabs.addChangeListener(e -> reload());
        JPanel north = new JPanel(new BorderLayout());
        north.add(Theme.title("Kho hàng"), BorderLayout.WEST);
        north.add(Fields.row(Theme.muted("Khoảng thời gian cho phiếu và lịch sử:"), range), BorderLayout.EAST);
        JPanel screen = new JPanel(new BorderLayout(0, 10));
        screen.setBorder(Theme.pad(14, 16));
        screen.add(north, BorderLayout.NORTH);
        screen.add(tabs, BorderLayout.CENTER);
        add(screen, BorderLayout.CENTER);
    }

    private LocalDate[] range() {
        return new LocalDate[]{Check.notNull(Fields.getDate(from, "Từ ngày"), "từ ngày"), Check.notNull(Fields.getDate(to, "Đến ngày"), "đến ngày")};
    }

    @Override
    public void reload() {
        if (tabs.getSelectedComponent() == movements) {
            UiTask.load(this, services.inventory::findNguyenLieu, list -> {
                Object sel = movementFilter.getSelectedItem();
                movementFilter.removeAllItems();
                movementFilter.addItem(ALL);
                list.forEach(movementFilter::addItem);
                movementFilter.setSelectedItem(sel == null ? ALL : sel);
                movements.reload();
            });
            return;
        }
        ((Refreshable) tabs.getSelectedComponent()).reload();
    }

    private void editIngredient(NguyenLieu n) {
        FormDialog f = new FormDialog(this, n == null ? "Thêm nguyên liệu" : "Sửa nguyên liệu");
        JTextField ten = Fields.text(n == null ? "" : n.tenNL());
        JTextField dvt = Fields.text(n == null ? "" : n.donViTinh());
        JTextField canhBao = Fields.decimal(n == null ? BigDecimal.ZERO : n.mucCanhBao());
        JTextField tonDau = Fields.decimal(BigDecimal.ZERO);
        JCheckBox active = Fields.check("Đang sử dụng", n == null || n.trangThai());
        f.addRow("Tên nguyên liệu *", ten).addRow("Đơn vị tính *", dvt).addRow("Mức cảnh báo tồn *", canhBao);
        if (n == null) {
            f.addRow("Tồn đầu", tonDau);
        } else {
            f.addWide(Theme.muted("Tồn kho chỉ thay đổi qua phiếu nhập/xuất, bán hàng hoặc điều chỉnh kiểm kê."));
        }
        f.addRow("", active);
        if (f.open(() -> {
            NguyenLieu input = new NguyenLieu(n == null ? null : n.maNL(), ten.getText(), dvt.getText(), null,
                    Fields.getDecimal(canhBao, "Mức cảnh báo"), active.isSelected());
            BigDecimal opening = n == null ? Fields.getDecimal(tonDau, "Tồn đầu") : null;
            return () -> services.inventory.saveNguyenLieu(input, opening);
        })) {
            ingredients.reload();
        }
    }

    private void adjustStock(NguyenLieu n) {
        FormDialog f = new FormDialog(this, "Điều chỉnh tồn kho - " + n.tenNL());
        JTextField actual = Fields.decimal(n.soLuongTon());
        JTextField reason = Fields.text("");
        f.addRow("Tồn trên hệ thống", new JLabel(n.soLuongTon().stripTrailingZeros().toPlainString() + " " + n.donViTinh()));
        f.addRow("Số lượng thực tế *", actual).addRow("Lý do *", reason);
        f.addWide(Theme.muted("Chênh lệch được ghi thành một biến động \"Điều chỉnh kiểm kê\" để truy vết."));
        f.setSaveText("Điều chỉnh");
        if (f.open(() -> {
            BigDecimal q = Fields.getDecimal(actual, "Số lượng thực tế");
            String r = reason.getText();
            return () -> services.inventory.dieuChinhTon(n.maNL(), q, r);
        })) {
            ingredients.reload();
        }
    }

    private void editSupplier(NhaCungCap n) {
        FormDialog f = new FormDialog(this, n == null ? "Thêm nhà cung cấp" : "Sửa nhà cung cấp");
        JTextField ten = Fields.text(n == null ? "" : n.tenNCC());
        JTextField sdt = Fields.text(n == null ? "" : n.soDienThoai());
        JTextField email = Fields.text(n == null ? "" : n.email());
        JTextField diaChi = Fields.text(n == null ? "" : n.diaChi());
        JTextField ghiChu = Fields.text(n == null ? "" : n.ghiChu());
        JCheckBox active = Fields.check("Đang giao dịch", n == null || n.trangThai());
        f.addRow("Tên nhà cung cấp *", ten).addRow("Điện thoại", sdt).addRow("Email", email).addRow("Địa chỉ", diaChi)
                .addRow("Ghi chú", ghiChu).addRow("", active);
        if (f.open(() -> {
            NhaCungCap input = new NhaCungCap(n == null ? null : n.maNCC(), ten.getText(), sdt.getText(), diaChi.getText(),
                    email.getText(), ghiChu.getText(), active.isSelected());
            return () -> services.inventory.saveNhaCungCap(input);
        })) {
            suppliers.reload();
        }
    }

    private void newVoucher(boolean receipt) {
        UiTask.load(this, () -> new Object[]{services.inventory.findNguyenLieu().stream().filter(NguyenLieu::trangThai).toList(),
                receipt ? services.inventory.findNhaCungCap(true) : null}, r -> {
            @SuppressWarnings("unchecked") List<NguyenLieu> nl = (List<NguyenLieu>) r[0];
            @SuppressWarnings("unchecked") List<NhaCungCap> ncc = (List<NhaCungCap>) r[1];
            if (receipt && ncc.isEmpty()) {
                Dialogs.info(this, "Chưa có nhà cung cấp đang giao dịch.");
                return;
            }
            if (VoucherDialog.show(this, services, nl, ncc)) {
                (receipt ? receipts : issues).reload();
            }
        });
    }

    private void showReceipt(int maPN) {
        UiTask.load(this, () -> services.inventory.findPhieuNhap(maPN), p -> {
            StringBuilder sb = new StringBuilder("<html><b>Phiếu nhập PN" + p.maPN() + "</b> - " + Dates.format(p.ngayNhap())
                    + "<br>Nhà cung cấp: " + p.tenNCC() + "<br>Người nhập: " + p.tenNV() + " - " + p.trangThai() + "<br><br><table>");
            for (ChiTietPhieuNhap l : p.chiTiet()) {
                sb.append("<tr><td>").append(l.tenNL()).append("</td><td align=right>").append(l.soLuong().stripTrailingZeros().toPlainString())
                        .append(' ').append(l.donViTinh()).append("</td><td align=right>").append(Money.format(l.giaNhap()))
                        .append("</td><td align=right>").append(Money.format(l.thanhTien())).append("</td></tr>");
            }
            sb.append("</table><br><b>Tổng: ").append(Money.format(p.tongTien())).append("</b></html>");
            Dialogs.info(this, sb.toString());
        });
    }

    private void showIssue(int maPX) {
        UiTask.load(this, () -> services.inventory.findPhieuXuat(maPX), p -> {
            StringBuilder sb = new StringBuilder("<html><b>Phiếu xuất PX" + p.maPX() + "</b> - " + Dates.format(p.ngayXuat())
                    + "<br>Lý do: " + p.lyDo() + "<br>Người xuất: " + p.tenNV() + " - " + p.trangThai() + "<br><br><table>");
            for (ChiTietPhieuXuat l : p.chiTiet()) {
                sb.append("<tr><td>").append(l.tenNL()).append("</td><td align=right>").append(l.soLuongXuat().stripTrailingZeros().toPlainString())
                        .append(' ').append(l.donViTinh()).append("</td><td align=right>").append(Money.format(l.donGiaXuat()))
                        .append("</td><td align=right>").append(Money.format(l.thanhTien())).append("</td></tr>");
            }
            sb.append("</table><br><b>Tổng: ").append(Money.format(p.tongTien())).append("</b></html>");
            Dialogs.info(this, sb.toString());
        });
    }

    private void cancelVoucher(int id, boolean receipt, TrangThaiPhieu status) {
        if (status == TrangThaiPhieu.DA_HUY) {
            Dialogs.info(this, "Phiếu này đã bị hủy.");
            return;
        }
        String label = receipt ? "phiếu nhập PN" + id : "phiếu xuất PX" + id;
        String reason = Dialogs.askReason(this, "Hủy phiếu", "Hủy " + label + " (tồn kho sẽ được điều chỉnh ngược lại).\nLý do:");
        if (reason == null) {
            return;
        }
        UiTask.exec(this, () -> {
            if (receipt) {
                services.inventory.huyPhieuNhap(id, reason);
            } else {
                services.inventory.huyPhieuXuat(id, reason);
            }
        }, () -> (receipt ? receipts : issues).reload());
    }

    /** Products on the left, the selected product's recipe on the right. */
    private final class RecipeTab extends JPanel implements Refreshable {
        private final DataTable<Mon> products = new DataTable<>(
                Column.text("Món", 180, Mon::tenMon),
                Column.text("Loại", 100, Mon::tenLoai));
        private final ListPanel<CongThuc> lines;
        private volatile Mon selectedMon;   // read by the background loader

        RecipeTab() {
            super(new BorderLayout());
            lines = new ListPanel<>(() -> {
                Mon m = selectedMon;
                return m == null ? List.of() : services.inventory.findCongThuc(m.maMon());
            }, new DataTable<>(
                    Column.text("Nguyên liệu", 180, CongThuc::tenNL),
                    Column.qty("Định lượng (size M)", 130, CongThuc::dinhLuong),
                    Column.text("ĐVT", 70, CongThuc::donViTinh)));
            lines.table.setEmptyText("Chọn món bên trái để xem công thức");
            lines.action(Theme.primary("Thêm nguyên liệu"), false, c -> editLine(null));
            lines.action(Theme.button("Sửa"), true, this::editLine);
            lines.action(Theme.danger("Xóa"), true, c -> {
                if (Dialogs.confirm(this, "Xóa \"" + c.tenNL() + "\" khỏi công thức?")) {
                    UiTask.exec(this, () -> services.inventory.deleteCongThuc(c.maCT()), lines::reload);
                }
            });
            products.onSelect(m -> {
                selectedMon = m;
                lines.reload();
            });
            products.setPreferredSize(new Dimension(320, 0));
            JPanel left = new JPanel(new BorderLayout(0, 6));
            left.setBorder(Theme.pad(10, 10));
            left.add(Theme.muted("Chọn món"), BorderLayout.NORTH);
            left.add(products, BorderLayout.CENTER);
            JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, lines);
            split.setResizeWeight(0.35);
            split.setBorder(null);
            add(split, BorderLayout.CENTER);
            JLabel hint = Theme.muted("  Định lượng tính cho 1 ly size M; size khác nhân với hệ số định lượng của size. "
                    + "Nguyên liệu được trừ kho khi hóa đơn được thanh toán.");
            add(hint, BorderLayout.SOUTH);
        }

        @Override
        public void reload() {
            UiTask.load(this, services.catalog::findMon, products::setRows);
            lines.reload();
        }

        private void editLine(CongThuc c) {
            Mon m = products.getSelected();
            if (m == null) {
                Dialogs.info(this, "Hãy chọn món trước.");
                return;
            }
            UiTask.load(this, services.inventory::findNguyenLieu, all -> {
                FormDialog f = new FormDialog(this, "Công thức - " + m.tenMon());
                JComboBox<NguyenLieu> nl = Fields.combo(all.stream().filter(NguyenLieu::trangThai).toList(),
                        c == null ? null : all.stream().filter(x -> x.maNL() == c.maNL()).findFirst().orElse(null));
                JTextField dl = Fields.decimal(c == null ? null : c.dinhLuong());
                f.addRow("Nguyên liệu *", nl).addRow("Định lượng (size M) *", dl);
                if (f.open(() -> {
                    NguyenLieu n = Check.notNull((NguyenLieu) nl.getSelectedItem(), "nguyên liệu");
                    BigDecimal q = Fields.getDecimal(dl, "Định lượng");
                    return () -> services.inventory.saveCongThuc(c == null ? null : c.maCT(), m.maMon(), n.maNL(), q);
                })) {
                    lines.reload();
                }
            });
        }
    }
}
