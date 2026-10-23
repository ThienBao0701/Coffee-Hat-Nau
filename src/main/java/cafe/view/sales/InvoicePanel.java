package cafe.view.sales;

import cafe.model.ChiTietHoaDon;
import cafe.model.HoaDon;
import cafe.model.InvoiceDetail;
import cafe.model.TrangThaiHoaDon;
import cafe.service.Services;
import cafe.util.Dates;
import cafe.util.Money;
import cafe.util.Permission;
import cafe.util.Session;
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
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.time.LocalDate;
import java.util.List;

/** Invoice history with details, receipt reprint and (manager) refunds. Employees only see their own invoices. */
public class InvoicePanel extends JPanel implements Refreshable {

    private static final String ALL = "Tất cả trạng thái";

    private final Services services;
    private final JTextField from = Fields.date(LocalDate.now());
    private final JTextField to = Fields.date(LocalDate.now());
    private final JComboBox<Object> status = new JComboBox<>();
    private final JTextField id = Fields.text("");
    private final DataTable<HoaDon> invoices = new DataTable<>(
            Column.integer("Mã HĐ", 60, HoaDon::maHD),
            Column.dateTime("Ngày lập", 120, HoaDon::ngayLap),
            Column.text("Bàn / loại", 100, h -> h.maBan() != null ? h.tenBan() : h.loaiDon()),
            Column.text("Nhân viên", 120, HoaDon::tenNV),
            Column.text("Khách hàng", 120, h -> h.tenKH() == null ? "" : h.tenKH()),
            Column.text("Trạng thái", 100, HoaDon::trangThai),
            Column.money("Tổng tiền", 100, h -> h.trangThai() == TrangThaiHoaDon.OPEN ? null : h.tongTien()));
    private final DataTable<ChiTietHoaDon> lines = new DataTable<>(
            Column.text("Món", 160, l -> l.tenMon() + " (" + l.tenSize() + ")"),
            Column.text("Topping", 150, ChiTietHoaDon::moTaTopping),
            Column.text("Ghi chú", 110, l -> l.ghiChu() == null ? "" : l.ghiChu()),
            Column.integer("SL", 40, ChiTietHoaDon::soLuong),
            Column.money("Đơn giá", 90, ChiTietHoaDon::donGia),
            Column.money("Thành tiền", 100, ChiTietHoaDon::tinhThanhTien));
    private final JLabel summary = new JLabel(" ");
    private final JButton receipt = Theme.button("Xem / in hóa đơn");
    private final JButton refund = Theme.danger("Hoàn tiền");
    private InvoiceDetail selected;

    public InvoicePanel(Services services) {
        super(new BorderLayout(0, 8));
        this.services = services;
        setBorder(Theme.pad(14, 16));
        status.addItem(ALL);
        for (TrangThaiHoaDon s : TrangThaiHoaDon.values()) {
            status.addItem(s);
        }
        id.setColumns(6);
        JButton search = Theme.primary("Tìm");
        search.addActionListener(e -> reload());
        JPanel top = new JPanel(new BorderLayout(0, 8));
        String title = Session.can(Permission.VIEW_ALL_INVOICES) ? "Hóa đơn" : "Hóa đơn của tôi";
        top.add(Theme.title(title), BorderLayout.NORTH);
        top.add(Fields.row(new JLabel("Từ ngày"), from, new JLabel("Đến ngày"), to, status, new JLabel("Mã HĐ"), id, search,
                new JLabel("   Lọc nhanh:"), Fields.search(invoices::setFilter)), BorderLayout.CENTER);
        add(top, BorderLayout.NORTH);

        invoices.setEmptyText("Không có hóa đơn trong khoảng thời gian này");
        invoices.onSelect(h -> {
            if (h == null) {
                showDetail(null);
            } else {
                UiTask.load(this, () -> services.orders.getInvoice(h.maHD()), this::showDetail);
            }
        });

        JPanel detail = Theme.card(new BorderLayout(0, 6));
        detail.add(summary, BorderLayout.NORTH);
        detail.add(lines, BorderLayout.CENTER);
        receipt.setEnabled(false);
        receipt.addActionListener(e -> {
            if (selected != null) {
                ReceiptDialog.show(this, selected, services.config);
            }
        });
        refund.addActionListener(e -> refund());
        refund.setVisible(Session.can(Permission.REFUND_INVOICE));
        detail.add(Fields.row(receipt, refund), BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, invoices, detail);
        split.setResizeWeight(0.55);
        split.setBorder(null);
        add(split, BorderLayout.CENTER);
        showDetail(null);
    }

    @Override
    public void reload() {
        LocalDate f;
        LocalDate t;
        Integer maHD;
        try {
            f = Fields.getDate(from, "Từ ngày");
            t = Fields.getDate(to, "Đến ngày");
            maHD = id.getText().isBlank() ? null : Fields.getInt(id, "Mã hóa đơn");
        } catch (RuntimeException e) {
            Dialogs.error(this, e);
            return;
        }
        TrangThaiHoaDon st = status.getSelectedItem() instanceof TrangThaiHoaDon s ? s : null;
        UiTask.load(this, () -> services.orders.search(f, t, st, maHD), invoices::setRows);
    }

    private void showDetail(InvoiceDetail d) {
        selected = d;
        if (d == null) {
            summary.setText("Chọn một hóa đơn để xem chi tiết.");
            lines.setRows(List.of());
            receipt.setEnabled(false);
            refund.setEnabled(false);
            return;
        }
        receipt.setEnabled(true);
        HoaDon h = d.hoaDon();
        StringBuilder s = new StringBuilder("<html><b>" + h.nhan() + "</b> &nbsp; " + h.trangThai());
        if (h.trangThai() == TrangThaiHoaDon.OPEN) {
            s.append(" &nbsp; Tạm tính: ").append(Money.format(d.tamTinh()));
        } else if (h.trangThai() != TrangThaiHoaDon.CANCELLED) {
            s.append("<br>Tạm tính ").append(Money.format(h.tamTinh()))
                    .append(" &nbsp;·&nbsp; Giảm ").append(Money.format(h.tienGiam())).append(h.tenKM() != null ? " (" + esc(h.tenKM()) + ")" : "")
                    .append(" &nbsp;·&nbsp; Thuế ").append(h.thueSuat().stripTrailingZeros().toPlainString()).append("% = ").append(Money.format(h.tienThue()))
                    .append(" &nbsp;·&nbsp; <b>Tổng ").append(Money.format(h.tongTien())).append("</b>");
        }
        if (d.thanhToan() != null) {
            s.append("<br>Thanh toán: ").append(d.thanhToan().phuongThuc()).append(" lúc ").append(Dates.format(d.thanhToan().thoiDiem()))
                    .append(" bởi ").append(esc(d.thanhToan().tenNV()));
        }
        if (h.lyDoHuy() != null) {
            s.append("<br>Lý do hủy/hoàn: ").append(esc(h.lyDoHuy())).append(" (").append(Dates.format(h.thoiDiemHuy())).append(")");
        }
        summary.setText(s.append("</html>").toString());
        lines.setRows(d.chiTiet());
        refund.setEnabled(h.trangThai() == TrangThaiHoaDon.PAID);
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;");
    }

    private void refund() {
        if (selected == null) {
            return;
        }
        HoaDon h = selected.hoaDon();
        String reason = Dialogs.askReason(this, "Hoàn tiền", "Hoàn " + Money.format(h.tongTien()) + " cho HĐ " + h.maHD() + ".\nLý do hoàn tiền:");
        if (reason == null || !Dialogs.confirm(this, "Xác nhận hoàn tiền toàn bộ HĐ " + h.maHD() + "? Thao tác này không thể hoàn tác.")) {
            return;
        }
        UiTask.exec(this, () -> services.orders.refund(h.maHD(), reason), () -> {
            Dialogs.info(this, "Đã hoàn tiền HĐ " + h.maHD() + ".");
            reload();
        });
    }
}
