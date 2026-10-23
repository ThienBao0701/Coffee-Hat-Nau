package cafe.view;

import cafe.model.DonDatHang;
import cafe.model.CashShift;
import cafe.model.HoaDon;
import cafe.model.NguyenLieu;
import cafe.model.Reports;
import cafe.service.ReportService;
import cafe.service.Services;
import cafe.util.Money;
import cafe.view.common.Column;
import cafe.view.common.DataTable;
import cafe.view.common.Refreshable;
import cafe.view.common.Theme;
import cafe.view.common.UiTask;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.time.format.DateTimeFormatter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/** Today at a glance. Employees see operational figures only; managers also see revenue and stock alerts. */
public class DashboardPanel extends JPanel implements Refreshable {

    private final Services services;
    // Keep cards wide enough for Vietnamese labels and values on ordinary desktop widths.
    private final JPanel metrics = new JPanel(new GridLayout(0, 4, 10, 10));
    private final JLabel paymentMix = Theme.muted(" ");
    private final DataTable<HoaDon> recent = new DataTable<>(
            Column.integer("HĐ", 40, HoaDon::maHD),
            Column.text("Giờ", 48, h -> h.ngayLap().format(DateTimeFormatter.ofPattern("HH:mm"))),
            Column.text("Bàn / DV", 78, h -> h.maBan() != null ? h.tenBan() : h.loaiDon()),
            Column.text("Nhân viên", 92, HoaDon::tenNV),
            Column.money("Tổng tiền", 92, HoaDon::tongTien));
    private final DataTable<DonDatHang> reservations = new DataTable<>(
            Column.text("Giờ", 50, d -> d.thoiDiemDat().format(DateTimeFormatter.ofPattern("HH:mm"))),
            Column.text("Bàn", 62, DonDatHang::tenBan),
            Column.text("Khách", 88, DonDatHang::tenKH),
            Column.integer("SL", 42, DonDatHang::soNguoi),
            Column.text("Trạng thái", 92, DonDatHang::trangThai));
    private final DataTable<NguyenLieu> lowStock = new DataTable<>(
            Column.text("Nguyên liệu", 116, NguyenLieu::tenNL),
            Column.qty("Tồn", 66, NguyenLieu::soLuongTon),
            Column.qty("Cảnh báo", 70, NguyenLieu::mucCanhBao),
            Column.text("ĐVT", 56, NguyenLieu::donViTinh));
    private final JPanel lists = new JPanel(new GridBagLayout());

    public DashboardPanel(Services services) {
        super(new BorderLayout(0, 12));
        this.services = services;
        setBorder(Theme.pad(14, 16));
        JPanel top = new JPanel(new BorderLayout());
        top.add(Theme.title("Tổng quan hôm nay"), BorderLayout.WEST);
        JButton refresh = Theme.button("Làm mới");
        refresh.addActionListener(e -> reload());
        top.add(refresh, BorderLayout.EAST);
        JPanel north = new JPanel(new BorderLayout(0, 12));
        north.add(top, BorderLayout.NORTH);
        north.add(metrics, BorderLayout.CENTER);
        north.add(paymentMix, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);
        recent.setEmptyText("Chưa có hóa đơn thanh toán hôm nay");
        reservations.setEmptyText("Không có đặt bàn sắp tới hôm nay");
        lowStock.setEmptyText("Không có nguyên liệu sắp hết");
        add(lists, BorderLayout.CENTER);
    }

    private static JPanel section(String title, JPanel table) {
        JPanel p = Theme.card(new BorderLayout(0, 6));
        JLabel l = new JLabel(title);
        l.setFont(Theme.BOLD);
        p.add(l, BorderLayout.NORTH);
        p.add(table, BorderLayout.CENTER);
        return p;
    }

    private static JPanel metric(String label, String value) {
        JPanel p = Theme.card(new BorderLayout(0, 4));
        p.add(Theme.muted(label), BorderLayout.NORTH);
        JLabel v = new JLabel(value);
        v.setFont(Theme.font(Font.BOLD, 20));
        v.setForeground(Theme.TEXT);
        v.setToolTipText(value);
        p.add(v, BorderLayout.CENTER);
        return p;
    }

    @Override
    public void reload() {
        UiTask.load(this, () -> new Object[]{services.reports.dashboard(), services.cashShifts.visibleActiveShift(), services.cashShifts.history()},
                r -> show((ReportService.Dashboard) r[0], (CashShift) r[1], cast(r[2])));
    }

    @SuppressWarnings("unchecked")
    private static List<CashShift> cast(Object x) { return (List<CashShift>) x; }

    private void show(ReportService.Dashboard d, CashShift shift, List<CashShift> shifts) {
        metrics.removeAll();
        lists.removeAll();
        Reports.RevenueSummary s = d.today();
        if (s != null) {
            metrics.add(metric("Doanh thu thuần hôm nay", Money.format(s.doanhThuThuan())));
            metrics.add(metric("Số hóa đơn đã thanh toán", String.valueOf(s.soHoaDon())));
            metrics.add(metric("Giá trị trung bình / hóa đơn", Money.format(s.trungBinhHoaDon())));
            String methodSummary = d.paymentMethodsToday().isEmpty() ? "Chưa có giao dịch"
                    : d.paymentMethodsToday().stream().map(x -> paymentMethodName(x.phuongThuc()) + " " + Money.format(x.tongThu()))
                    .collect(Collectors.joining("  ·  "));
            paymentMix.setText("Thanh toán hôm nay: " + methodSummary);
        } else {
            metrics.add(metric("Hóa đơn tôi đã thu hôm nay", String.valueOf(d.myPaidToday())));
            metrics.add(metric("Tổng tiền tôi đã thu", Money.format(d.myCollectedToday())));
            paymentMix.setText(" ");
        }
        metrics.add(metric("Bàn đang có khách", d.occupiedTables() + " / " + d.activeTables()));
        metrics.add(metric("Ca thu ngân", shift == null ? "Chưa mở" : "#" + shift.maCa() + " · " + shift.tenNV()));
        LocalDate today = LocalDate.now();
        List<CashShift> closedToday = shifts.stream().filter(x -> !x.dangMo() && x.dongLuc() != null
                && x.dongLuc().toLocalDate().equals(today)).toList();
        BigDecimal varianceToday = closedToday.stream().map(CashShift::chenhLech).filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        metrics.add(metric("Ca đã đóng hôm nay", closedToday.size() + " · lệch " + Money.format(varianceToday)));
        if (d.lowStock() != null) {
            metrics.add(metric("Nguyên liệu sắp hết", String.valueOf(d.lowStock().size())));
        }
        recent.setRows(d.recentInvoices());
        reservations.setRows(d.upcomingReservations());
        JPanel recentSection = section(s != null ? "Giao dịch gần đây" : "Hóa đơn của tôi hôm nay", recent);
        JPanel reservationsSection = section("Đặt bàn sắp tới", reservations);
        GridBagConstraints mainList = new GridBagConstraints();
        mainList.gridx = 0;
        mainList.gridy = 0;
        mainList.weightx = 0.58;
        mainList.weighty = 1;
        mainList.gridheight = d.lowStock() == null ? 1 : 2;
        mainList.fill = GridBagConstraints.BOTH;
        mainList.insets = new Insets(0, 0, 0, 10);
        lists.add(recentSection, mainList);

        GridBagConstraints rightList = new GridBagConstraints();
        rightList.gridx = 1;
        rightList.gridy = 0;
        rightList.weightx = 0.42;
        rightList.weighty = d.lowStock() == null ? 1 : 0.56;
        rightList.fill = GridBagConstraints.BOTH;
        rightList.insets = new Insets(0, 0, d.lowStock() == null ? 0 : 8, 0);
        lists.add(reservationsSection, rightList);
        if (d.lowStock() != null) {
            lowStock.setRows(d.lowStock());
            GridBagConstraints stockList = new GridBagConstraints();
            stockList.gridx = 1;
            stockList.gridy = 1;
            stockList.weightx = 0.42;
            stockList.weighty = 0.44;
            stockList.fill = GridBagConstraints.BOTH;
            lists.add(section("Cảnh báo tồn kho", lowStock), stockList);
        }
        revalidate();
        repaint();
    }

    private static String paymentMethodName(String method) {
        return switch (method) {
            case "TIEN_MAT" -> "Tiền mặt";
            case "CHUYEN_KHOAN" -> "Chuyển khoản";
            case "THE" -> "Thẻ";
            default -> method;
        };
    }
}
