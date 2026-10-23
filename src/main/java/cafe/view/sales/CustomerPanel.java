package cafe.view.sales;

import cafe.model.Ban;
import cafe.model.DonDatHang;
import cafe.model.KhachHang;
import cafe.model.TrangThaiDatBan;
import cafe.service.Services;
import cafe.util.Check;
import cafe.util.Permission;
import cafe.util.Session;
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
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/** Customers and table reservations (create, confirm, cancel, check-in). */
public class CustomerPanel extends JPanel implements Refreshable {

    private static final String ALL = "Tất cả trạng thái";

    private final Services services;
    private final ListPanel<KhachHang> customers;
    private final ListPanel<DonDatHang> reservations;
    private final JTextField from = Fields.date(LocalDate.now());
    private final JTextField to = Fields.date(LocalDate.now().plusDays(7));
    private final JComboBox<Object> status = new JComboBox<>();
    private final JTabbedPane tabs = new JTabbedPane();

    public CustomerPanel(Services services) {
        super(new BorderLayout());
        this.services = services;

        customers = new ListPanel<>(() -> services.customers.search(null), new DataTable<>(
                Column.integer("Mã", 50, KhachHang::maKH),
                Column.text("Tên khách hàng", 180, KhachHang::tenKH),
                Column.text("Số điện thoại", 110, k -> k.soDienThoai() == null ? "" : k.soDienThoai()),
                Column.date("Ngày sinh", 90, KhachHang::ngaySinh),
                Column.text("CMND/CCCD", 110, k -> k.soCMND() == null ? "" : k.soCMND()),
                Column.text("Địa chỉ", 220, k -> k.diaChi() == null ? "" : k.diaChi())));
        customers.action(Theme.primary("Thêm khách hàng"), false, k -> editCustomer(null));
        customers.action(Theme.button("Sửa"), true, this::editCustomer);
        if (Session.can(Permission.DELETE_CUSTOMER)) {
            customers.action(Theme.danger("Xóa"), true, this::deleteCustomer);
        }
        customers.table.onDoubleClick(this::editCustomer);

        status.addItem(ALL);
        for (TrangThaiDatBan s : TrangThaiDatBan.values()) {
            status.addItem(s);
        }
        reservations = new ListPanel<>(this::loadReservations, new DataTable<>(
                Column.integer("Mã", 50, DonDatHang::maDDH),
                Column.dateTime("Thời điểm đặt", 120, DonDatHang::thoiDiemDat),
                Column.text("Bàn", 80, DonDatHang::tenBan),
                Column.text("Khách hàng", 150, DonDatHang::tenKH),
                Column.text("SĐT", 100, DonDatHang::sdtKH),
                Column.integer("Số người", 60, DonDatHang::soNguoi),
                Column.text("Trạng thái", 100, DonDatHang::trangThai),
                Column.text("Ghi chú", 160, d -> d.ghiChu() == null ? "" : d.ghiChu()),
                Column.text("Người nhận", 110, DonDatHang::tenNV)));
        reservations.action(Theme.primary("Đặt bàn mới"), false, d -> newReservation());
        reservations.action(Theme.button("Xác nhận"), true, d -> act(d, "Xác nhận đơn đặt bàn " + d.maDDH() + "?",
                () -> services.customers.confirmReservation(d.maDDH())));
        reservations.action(Theme.button("Nhận bàn"), true, this::checkIn);
        reservations.action(Theme.danger("Hủy đặt bàn"), true, d -> act(d, "Hủy đơn đặt bàn " + d.maDDH() + " của " + d.tenKH() + "?",
                () -> services.customers.cancelReservation(d.maDDH())));
        JButton filter = Theme.button("Lọc");
        filter.addActionListener(e -> reservations.reload());
        reservations.addFilter(Fields.row(new JLabel("Từ"), from, new JLabel("đến"), to, status, filter));
        from.setColumns(8);
        to.setColumns(8);

        tabs.addTab("Khách hàng", customers);
        tabs.addTab("Đặt bàn", reservations);
        tabs.addChangeListener(e -> reload());
        add(Theme.screen("Khách hàng & đặt bàn", tabs), BorderLayout.CENTER);
    }

    @Override
    public void reload() {
        ((Refreshable) tabs.getSelectedComponent()).reload();
    }

    private List<DonDatHang> loadReservations() {
        LocalDate f = Fields.getDate(from, "Từ ngày");
        LocalDate t = Fields.getDate(to, "Đến ngày");
        TrangThaiDatBan st = status.getSelectedItem() instanceof TrangThaiDatBan s ? s : null;
        return services.customers.findReservations(Check.notNull(f, "từ ngày"), Check.notNull(t, "đến ngày"), st);
    }

    private void editCustomer(KhachHang k) {
        boolean canSeeId = Session.can(Permission.VIEW_CUSTOMER_ID);
        FormDialog f = new FormDialog(this, k == null ? "Thêm khách hàng" : "Sửa khách hàng");
        JTextField ten = Fields.text(k == null ? "" : k.tenKH());
        JTextField sdt = Fields.text(k == null ? "" : k.soDienThoai());
        JTextField ngaySinh = Fields.date(k == null ? null : k.ngaySinh());
        JTextField cmnd = Fields.text(k == null ? "" : k.soCMND());
        cmnd.setEnabled(k == null || canSeeId);
        JTextField diaChi = Fields.text(k == null ? "" : k.diaChi());
        f.addRow("Tên khách hàng *", ten).addRow("Số điện thoại", sdt).addRow("Ngày sinh (dd/MM/yyyy)", ngaySinh)
                .addRow("CMND/CCCD", cmnd).addRow("Địa chỉ", diaChi);
        if (!canSeeId) {
            f.addWide(Theme.muted("Số CMND/CCCD chỉ quản lý được xem đầy đủ."));
        }
        if (f.open(() -> {
            KhachHang input = new KhachHang(k == null ? null : k.maKH(), ten.getText(), Fields.getDate(ngaySinh, "Ngày sinh"),
                    cmnd.getText(), sdt.getText(), diaChi.getText());
            return () -> services.customers.save(input);
        })) {
            customers.reload();
        }
    }

    private void deleteCustomer(KhachHang k) {
        if (Dialogs.confirm(this, "Xóa khách hàng \"" + k.tenKH() + "\"?")) {
            UiTask.exec(this, () -> services.customers.delete(k.maKH()), customers::reload);
        }
    }

    private void newReservation() {
        UiTask.load(this, () -> services.tables.findBan(true), tables -> {
            FormDialog f = new FormDialog(this, "Đặt bàn mới");
            JTextField sdt = Fields.text("");
            JTextField ten = Fields.text("");
            JComboBox<Ban> ban = Fields.combo(tables, null);
            JTextField when = Fields.dateTime(LocalDateTime.of(LocalDate.now().plusDays(1), LocalTime.of(19, 0)));
            JTextField soNguoi = Fields.text("2");
            JTextField ghiChu = Fields.text("");
            f.addRow("SĐT khách *", sdt).addRow("Tên khách *", ten).addRow("Bàn *", ban)
                    .addRow("Thời điểm (dd/MM/yyyy HH:mm) *", when).addRow("Số người *", soNguoi).addRow("Ghi chú", ghiChu);
            f.addWide(Theme.muted("Nếu số điện thoại đã có, hệ thống dùng lại khách hàng cũ."));
            f.setSaveText("Đặt bàn");
            if (f.open(() -> {
                Ban b = Check.notNull((Ban) ban.getSelectedItem(), "bàn");
                LocalDateTime t = Check.notNull(Fields.getDateTime(when, "Thời điểm"), "thời điểm");
                int people = Fields.getInt(soNguoi, "Số người");
                String phone = sdt.getText();
                String name = ten.getText().isBlank() ? phone : ten.getText();
                String note = ghiChu.getText();
                return () -> services.customers.createReservation(null, name, phone, b.maBan(), t, people, note);
            })) {
                reservations.reload();
            }
        });
    }

    private void act(DonDatHang d, String question, Runnable action) {
        if (Dialogs.confirm(this, question)) {
            UiTask.exec(this, action, reservations::reload);
        }
    }

    private void checkIn(DonDatHang d) {
        if (!Dialogs.confirm(this, "Khách " + d.tenKH() + " đã đến? Mở hóa đơn cho " + d.tenBan() + ".")) {
            return;
        }
        UiTask.load(this, () -> services.orders.checkInReservation(d.maDDH()), id -> {
            Dialogs.info(this, "Đã mở hóa đơn HĐ " + id + " cho " + d.tenBan() + ". Chuyển sang màn hình Bán hàng để gọi món.");
            reservations.reload();
        });
    }
}
