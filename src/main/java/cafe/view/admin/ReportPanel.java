package cafe.view.admin;

import cafe.model.Reports;
import cafe.model.CashShift;
import cafe.model.CashShiftAdjustment;
import cafe.service.Services;
import cafe.util.Check;
import cafe.util.Money;
import cafe.view.common.Column;
import cafe.view.common.DataTable;
import cafe.view.common.Dialogs;
import cafe.view.common.Fields;
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
import java.awt.Font;
import java.awt.GridLayout;
import java.time.LocalDate;
import java.util.List;

/** Revenue, best sellers, sales by employee and stock movement reports for a date range (manager). */
public class ReportPanel extends JPanel implements Refreshable {

    private final Services services;
    private final JTextField from = Fields.date(LocalDate.now().withDayOfMonth(1));
    private final JTextField to = Fields.date(LocalDate.now());
    private final JComboBox<String> employeeFilter = new JComboBox<>(new String[]{"Tất cả nhân viên"});
    private final JComboBox<String> methodFilter = new JComboBox<>(new String[]{"Tất cả phương thức", "TIEN_MAT", "CHUYEN_KHOAN", "THE"});
    private final JPanel summary = new JPanel(new GridLayout(3, 3, 10, 10));
    private final DataTable<Reports.RevenueRow> daily = revenueTable("Ngày");
    private final DataTable<Reports.RevenueRow> monthly = revenueTable("Tháng");
    private final DataTable<Reports.ProductSales> products = new DataTable<>(
            Column.text("Món", 220, Reports.ProductSales::tenMon),
            Column.integer("Số lượng bán", 110, Reports.ProductSales::soLuong),
            Column.money("Doanh số (gồm topping)", 160, Reports.ProductSales::doanhSo));
    private final DataTable<Reports.EmployeeSales> employees = new DataTable<>(
            Column.integer("Mã NV", 60, Reports.EmployeeSales::maNV),
            Column.text("Nhân viên tạo đơn", 200, Reports.EmployeeSales::tenNV),
            Column.integer("Số hóa đơn", 100, Reports.EmployeeSales::soHoaDon),
            Column.money("Doanh số sau giảm", 150, Reports.EmployeeSales::sauGiam),
            Column.money("Tổng thu (gồm thuế)", 150, Reports.EmployeeSales::tongThu));
    private final DataTable<Reports.PaymentMethodTotal> methods = new DataTable<>(
            Column.text("Phương thức", 150, Reports.PaymentMethodTotal::phuongThuc),
            Column.integer("Giao dịch", 100, Reports.PaymentMethodTotal::soGiaoDich),
            Column.money("Tổng thu", 150, Reports.PaymentMethodTotal::tongThu));
    private final DataTable<Reports.CashierSales> cashiers = new DataTable<>(
            Column.integer("Mã thu ngân", 90, Reports.CashierSales::maNV),
            Column.text("Thu ngân", 180, Reports.CashierSales::tenNV),
            Column.integer("Hóa đơn", 90, Reports.CashierSales::soHoaDon),
            Column.money("Tổng thu", 150, Reports.CashierSales::tongThu));
    private final DataTable<Reports.TransactionRow> transactions = new DataTable<>(
            Column.integer("HĐ", 60, Reports.TransactionRow::maHD),
            Column.text("Thời điểm", 150, Reports.TransactionRow::thoiDiem),
            Column.text("Trạng thái", 90, Reports.TransactionRow::trangThai),
            Column.text("NV tạo đơn", 160, x -> x.maNhanVien() + " · " + x.nhanVien()),
            Column.text("Thu ngân", 160, x -> x.maThuNgan() + " · " + x.thuNgan()),
            Column.text("Phương thức", 120, Reports.TransactionRow::phuongThuc),
            Column.money("Đã thu", 120, Reports.TransactionRow::tongTien));
    private final DataTable<CashShift> shifts = new DataTable<>(
            Column.integer("Mã ca", 60, CashShift::maCa), Column.text("Thu ngân", 150, CashShift::tenNV),
            Column.text("Mở lúc", 145, x -> x.moLuc().toString()), Column.text("Đóng lúc", 145, x -> x.dongLuc() == null ? "" : x.dongLuc().toString()),
            Column.money("Đầu ca", 105, CashShift::tienDauCa), Column.money("Kỳ vọng", 105, CashShift::tienKyVong),
            Column.money("Đã đếm", 105, CashShift::tienDem), Column.money("Chênh lệch", 105, CashShift::chenhLech),
            Column.text("Ghi chú", 220, CashShift::ghiChuDong));
    private final DataTable<CashShiftAdjustment> corrections = new DataTable<>(
            Column.integer("Mã ca", 70, CashShiftAdjustment::maCa),
            Column.text("Thời điểm", 150, x -> x.thoiDiem().toString()),
            Column.text("Người điều chỉnh", 180, x -> x.maNV() + " · " + x.tenNV()),
            Column.money("Tác động kỳ vọng", 150, CashShiftAdjustment::tacDongKyVong),
            Column.text("Lý do", 360, CashShiftAdjustment::lyDo));
    private final DataTable<Reports.StockRow> stock = new DataTable<>(
            Column.text("Nguyên liệu", 200, Reports.StockRow::tenNL),
            Column.text("ĐVT", 70, Reports.StockRow::donViTinh),
            Column.qty("Tồn đầu kỳ", 110, Reports.StockRow::tonDau),
            Column.qty("Nhập trong kỳ", 110, Reports.StockRow::nhap),
            Column.qty("Xuất / bán trong kỳ", 130, Reports.StockRow::xuat),
            Column.qty("Tồn cuối kỳ", 110, Reports.StockRow::tonCuoi));

    public ReportPanel(Services services) {
        super(new BorderLayout(0, 10));
        this.services = services;
        setBorder(Theme.pad(14, 16));
        JButton run = Theme.primary("Xem báo cáo");
        run.addActionListener(e -> reload());
        JButton today = Theme.button("Hôm nay");
        today.addActionListener(e -> setRange(LocalDate.now(), LocalDate.now()));
        JButton month = Theme.button("Tháng này");
        month.addActionListener(e -> setRange(LocalDate.now().withDayOfMonth(1), LocalDate.now()));
        JPanel top = new JPanel(new BorderLayout(0, 8));
        top.add(Theme.title("Báo cáo"), BorderLayout.NORTH);
        top.add(Fields.row(new JLabel("Từ ngày"), from, new JLabel("Đến ngày"), to, run, today, month), BorderLayout.CENTER);
        top.add(Fields.row(new JLabel("Nhân viên tạo đơn"), employeeFilter, new JLabel("Phương thức"), methodFilter), BorderLayout.SOUTH);
        JPanel north = new JPanel(new BorderLayout(0, 10));
        north.add(top, BorderLayout.NORTH);
        north.add(summary, BorderLayout.CENTER);
        north.add(Theme.muted("<html>Doanh số = tổng tạm tính các hóa đơn đã thanh toán trong kỳ (theo thời điểm thanh toán).<br>"
                + "Doanh thu thuần = doanh số − giảm giá − hoàn tiền (không gồm thuế). Hóa đơn chưa thanh toán hoặc đã hủy không được tính.</html>"),
                BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        tabs.addTab("Theo ngày", pad(daily));
        tabs.addTab("Theo tháng", pad(monthly));
        tabs.addTab("Món bán chạy", pad(products));
        tabs.addTab("Theo nhân viên", pad(employees));
        tabs.addTab("Theo phương thức", pad(methods));
        tabs.addTab("Theo thu ngân", pad(cashiers));
        tabs.addTab("Giao dịch", pad(transactions));
        tabs.addTab("Nhập - xuất - tồn kho", pad(stock));
        tabs.addTab("Ca & chênh lệch", pad(shifts));
        tabs.addTab("Điều chỉnh ca", pad(corrections));
        add(tabs, BorderLayout.CENTER);
    }

    private static JPanel pad(JPanel p) {
        JPanel w = new JPanel(new BorderLayout());
        w.setBorder(Theme.pad(8, 8));
        w.add(p);
        return w;
    }

    private static DataTable<Reports.RevenueRow> revenueTable(String period) {
        return new DataTable<>(
                Column.text(period, 100, Reports.RevenueRow::ky),
                Column.integer("Số hóa đơn", 90, Reports.RevenueRow::soHoaDon),
                Column.money("Doanh số", 120, Reports.RevenueRow::doanhSo),
                Column.money("Giảm giá", 110, Reports.RevenueRow::giamGia),
                Column.money("Sau giảm giá", 120, Reports.RevenueRow::sauGiam),
                Column.money("Thuế", 100, Reports.RevenueRow::thue),
                Column.money("Tổng thu", 120, Reports.RevenueRow::tongThu));
    }

    private void setRange(LocalDate f, LocalDate t) {
        from.setText(cafe.util.Dates.format(f));
        to.setText(cafe.util.Dates.format(t));
        reload();
    }

    @Override
    public void reload() {
        LocalDate f;
        LocalDate t;
        try {
            f = Check.notNull(Fields.getDate(from, "Từ ngày"), "từ ngày");
            t = Check.notNull(Fields.getDate(to, "Đến ngày"), "đến ngày");
            Check.dateRange(f, t, "báo cáo");
        } catch (RuntimeException e) {
            Dialogs.error(this, e);
            return;
        }
        String employeeSelection = (String) employeeFilter.getSelectedItem();
        String methodSelection = (String) methodFilter.getSelectedItem();
        Integer selectedEmployee = parseEmployeeId(employeeSelection);
        String selectedMethod = methodSelection == null || methodSelection.startsWith("Tất cả") ? null : methodSelection;
        UiTask.load(this, () -> new Object[]{services.reports.summary(f, t), services.reports.byDay(f, t), services.reports.byMonth(f, t),
                services.reports.productSales(f, t), services.reports.employeeSales(f, t), services.reports.stock(f, t),
                services.reports.paymentMethods(f, t), services.reports.cashierSales(f, t), services.reports.transactions(f, t),
                services.cashShifts.history(), services.employees.findAll(),
                services.reports.transactions(f, t, selectedEmployee, selectedMethod), services.cashShifts.adjustmentHistory()}, r -> {
            showSummary((Reports.RevenueSummary) r[0]);
            daily.setRows(cast(r[1]));
            monthly.setRows(cast(r[2]));
            products.setRows(cast(r[3]));
            employees.setRows(cast(r[4]));
            stock.setRows(cast(r[5]));
            methods.setRows(cast(r[6]));
            cashiers.setRows(cast(r[7]));
            transactions.setRows(cast(r[11]));
            shifts.setRows(cast(r[9]));
            corrections.setRows(cast(r[12]));
            @SuppressWarnings("unchecked") List<cafe.model.NhanVien> staff = (List<cafe.model.NhanVien>) r[10];
            String selection = (String) employeeFilter.getSelectedItem();
            employeeFilter.removeAllItems();
            employeeFilter.addItem("Tất cả nhân viên");
            for (cafe.model.NhanVien n : staff) employeeFilter.addItem(n.maNV() + " · " + n.tenNV());
            if (selection != null) employeeFilter.setSelectedItem(selection);
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> cast(Object o) {
        return (List<T>) o;
    }

    private static Integer parseEmployeeId(String selection) {
        if (selection == null || selection.startsWith("Tất cả")) return null;
        int separator = selection.indexOf(" · ");
        if (separator < 1) return null;
        try { return Integer.parseInt(selection.substring(0, separator)); }
        catch (NumberFormatException e) { return null; }
    }

    private void showSummary(Reports.RevenueSummary s) {
        summary.removeAll();
        summary.add(metric("Số hóa đơn đã thanh toán", String.valueOf(s.soHoaDon())));
        summary.add(metric("Doanh số", Money.format(s.doanhSo())));
        summary.add(metric("Giảm giá", Money.format(s.giamGia())));
        summary.add(metric("Hoàn tiền (" + s.soHoaDonHoan() + " HĐ)", Money.format(s.hoanTien())));
        summary.add(metric("Doanh thu thuần", Money.format(s.doanhThuThuan())));
        summary.add(metric("Thuế thu hộ", Money.format(s.thue())));
        summary.add(metric("Tổng tiền đã thu", Money.format(s.tongThu())));
        summary.add(metric("Trung bình / hóa đơn", Money.format(s.trungBinhHoaDon())));
        summary.add(metric("Hóa đơn đã hủy", String.valueOf(s.soHoaDonHuy())));
        summary.revalidate();
        summary.repaint();
    }

    private static JPanel metric(String label, String value) {
        JPanel p = Theme.card(new BorderLayout(0, 2));
        p.add(Theme.muted(label), BorderLayout.NORTH);
        JLabel v = new JLabel(value);
        v.setFont(Theme.font(Font.BOLD, 17));
        p.add(v, BorderLayout.CENTER);
        return p;
    }
}
