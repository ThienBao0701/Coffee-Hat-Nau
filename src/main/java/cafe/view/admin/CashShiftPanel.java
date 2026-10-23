package cafe.view.admin;

import cafe.model.CashMovementKind;
import cafe.model.CashShift;
import cafe.model.CashShiftAdjustment;
import cafe.service.CashShiftService;
import cafe.service.Services;
import cafe.util.Money;
import cafe.util.Permission;
import cafe.util.Session;
import cafe.view.common.Column;
import cafe.view.common.DataTable;
import cafe.view.common.Dialogs;
import cafe.view.common.Fields;
import cafe.view.common.FormDialog;
import cafe.view.common.Refreshable;
import cafe.view.common.Theme;
import cafe.view.common.UiTask;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/** Drawer ownership, cash activity and immutable shift closeouts. */
public final class CashShiftPanel extends JPanel implements Refreshable {
    private final Services services;
    private final JLabel status = new JLabel("Đang tải ca thu ngân…");
    private final JLabel detail = Theme.muted(" ");
    private final JPanel figures = new JPanel(new BorderLayout());
    private final javax.swing.JButton openAction = Theme.primary("Mở ca");
    private final javax.swing.JButton movementAction = Theme.button("Ghi thu / chi / rút tiền");
    private final javax.swing.JButton closeAction = Theme.danger("Đếm và đóng ca");
    private final DataTable<CashShift> rows = new DataTable<>(
            Column.integer("Mã ca", 65, CashShift::maCa),
            Column.text("Thu ngân", 140, CashShift::tenNV),
            Column.text("Mở lúc", 145, s -> s.moLuc().format(DateTimeFormatter.ofPattern("dd/MM HH:mm"))),
            Column.money("Đầu ca", 110, CashShift::tienDauCa),
            Column.text("Trạng thái", 90, s -> s.dangMo() ? "Đang mở" : "Đã đóng"),
            Column.money("Kỳ vọng", 110, CashShift::tienKyVong),
            Column.money("Đã đếm", 110, CashShift::tienDem),
            Column.money("Chênh lệch", 110, CashShift::chenhLech));

    public CashShiftPanel(Services services) {
        super(new BorderLayout(0, 10));
        this.services = services;
        setBorder(Theme.pad(14, 16));
        rows.setRowColor(shift -> !shift.dangMo() && shift.chenhLech() != null && shift.chenhLech().signum() != 0
                ? Theme.WARNING_BG : null);
        JPanel summary = new JPanel();
        summary.setOpaque(false);
        summary.setLayout(new javax.swing.BoxLayout(summary, javax.swing.BoxLayout.Y_AXIS));
        JPanel heading = new JPanel(new BorderLayout(0, 4));
        heading.setOpaque(false);
        heading.add(Theme.title("Ca thu ngân & đối soát tiền mặt"), BorderLayout.NORTH);
        status.setFont(Theme.font(Font.BOLD, 16));
        status.setForeground(Theme.TEXT);
        heading.add(status, BorderLayout.CENTER);
        heading.add(detail, BorderLayout.SOUTH);
        summary.add(heading);
        summary.add(javax.swing.Box.createVerticalStrut(10));
        figures.setOpaque(false);
        figures.add(new cafe.view.common.EmptyState("Đang tải số liệu", "Thông tin ca thu ngân sẽ xuất hiện tại đây."), BorderLayout.CENTER);
        summary.add(figures);
        summary.add(javax.swing.Box.createVerticalStrut(8));
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        var refresh = Theme.button("Làm mới");
        openAction.addActionListener(e -> openShift());
        movementAction.addActionListener(e -> movement());
        closeAction.addActionListener(e -> closeShift());
        refresh.addActionListener(e -> reload());
        actions.add(openAction); actions.add(movementAction); actions.add(closeAction); actions.add(refresh);
        if (Session.can(Permission.VIEW_REPORTS)) {
            var correction = Theme.button("Điều chỉnh ca đã đóng");
            var adjustmentHistory = Theme.button("Lịch sử điều chỉnh");
            correction.addActionListener(e -> correctClosedShift());
            adjustmentHistory.addActionListener(e -> showAdjustments());
            actions.add(correction);
            actions.add(adjustmentHistory);
        }
        summary.add(actions);
        add(summary, BorderLayout.NORTH);
        rows.setEmptyText("Chưa có ca thu ngân.");
        rows.onClick(shift -> {
            if (Session.can(Permission.VIEW_REPORTS) && !shift.dangMo()) {
                showAdjustments(shift);
            }
        });
        add(rows, BorderLayout.CENTER);
    }

    @Override
    public void reload() {
        UiTask.load(this, () -> {
            CashShift active = services.cashShifts.visibleActiveShift();
            return new Object[]{active, services.cashShifts.history(), active == null ? null : services.cashShifts.summary(active.maCa())};
        }, result -> {
            CashShift active = (CashShift) result[0];
            @SuppressWarnings("unchecked") List<CashShift> history = (List<CashShift>) result[1];
            CashShiftService.Summary s = (CashShiftService.Summary) result[2];
            rows.setRows(history);
            boolean ownsShift = active != null && active.maNV() == Session.current().maNV();
            openAction.setEnabled(active == null);
            movementAction.setEnabled(ownsShift);
            closeAction.setEnabled(ownsShift || active != null && Session.can(Permission.VIEW_REPORTS));
            if (active == null) {
                status.setText("Chưa có ca đang mở");
                status.setForeground(Theme.MUTED);
                detail.setText("Mở ca trước khi nhận thanh toán. Tiền đầu ca là tiền lẻ dự phòng, không phải doanh thu.");
                figures.removeAll();
                figures.setLayout(new BorderLayout());
                figures.add(new cafe.view.common.EmptyState("Chưa có ca đang mở",
                        "Mở ngăn tiền để bắt đầu nhận thanh toán và ghi nhận thu chi."), BorderLayout.CENTER);
            } else {
                status.setText("Ca #" + active.maCa() + " · " + active.tenNV() + " · ĐANG MỞ");
                status.setForeground(Theme.PRIMARY_DARK);
                var t = s.totals();
                detail.setText("Thu ngân: " + active.tenNV() + "  ·  Mở lúc "
                        + active.moLuc().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                        + "  ·  Tiền đầu ca là tiền lẻ dự phòng, không phải doanh thu.");
                figures.removeAll();
                figures.setLayout(new GridLayout(2, 5, 8, 8));
                figures.add(metric("Tiền đầu ca", Money.format(active.tienDauCa()), Theme.ACCENT));
                figures.add(metric("Bán tiền mặt", Money.format(t.cashSales()), Theme.PRIMARY));
                figures.add(metric("Hoàn tiền mặt", Money.format(t.cashRefunds()), Theme.INFO));
                figures.add(metric("Tiền thêm", Money.format(t.additions()), Theme.INFO));
                figures.add(metric("Tiền chi", Money.format(t.expenses()), Theme.WARNING_TEXT));
                figures.add(metric("Tiền rút", Money.format(t.withdrawals()), Theme.MUTED));
                figures.add(metric("Tiền kỳ vọng", Money.format(s.expected()), Theme.PRIMARY_DARK));
                figures.add(metric("Đã kiểm đếm", "Chưa đóng ca", Theme.MUTED));
                figures.add(metric("Chênh lệch", "Chưa đóng ca", Theme.MUTED));
                figures.add(metric("Trạng thái", "Đang mở", Theme.PRIMARY_DARK));
            }
            figures.revalidate();
            figures.repaint();
        });
    }

    private static JPanel metric(String label, String value, java.awt.Color accent) {
        JPanel card = Theme.card(new BorderLayout(0, 5));
        JLabel caption = Theme.muted(label);
        JLabel number = new JLabel(value);
        number.setFont(Theme.font(Font.BOLD, 16));
        number.setForeground(accent);
        number.setToolTipText(value);
        card.add(caption, BorderLayout.NORTH);
        card.add(number, BorderLayout.CENTER);
        return card;
    }

    private void openShift() {
        FormDialog f = new FormDialog(this, "Mở ca thu ngân");
        JTextField amount = Fields.money(java.math.BigDecimal.ZERO);
        f.addWide(Theme.muted("Tiền đầu ca là tiền lẻ dự phòng trong ngăn, không được tính vào doanh thu."));
        f.addRow("Tiền đầu ca (VNĐ)", amount);
        f.setSaveText("Xác nhận mở ca");
        if (f.open(() -> {
            var opening = Fields.getMoney(amount, "Tiền đầu ca");
            return () -> services.cashShifts.openShift(opening);
        })) reload();
    }

    private void movement() {
        FormDialog f = new FormDialog(this, "Ghi biến động tiền mặt");
        JComboBox<CashMovementKind> kind = Fields.combo(List.of(CashMovementKind.values()), CashMovementKind.CHI);
        JTextField amount = Fields.money(null);
        JTextField reason = Fields.text("");
        f.addRow("Loại", kind).addRow("Số tiền (VNĐ)", amount).addRow("Lý do bắt buộc", reason);
        f.addWide(Theme.muted("Bán hàng và hoàn tiền được lấy từ giao dịch gốc; không ghi lại chúng ở đây."));
        String requestKey = UUID.randomUUID().toString();
        f.setSaveText("Lưu biến động");
        if (f.open(() -> {
            var selected = (CashMovementKind) kind.getSelectedItem();
            var value = Fields.getMoney(amount, "Số tiền");
            var why = reason.getText();
            return () -> services.cashShifts.recordMovement(selected, value, why, requestKey);
        })) reload();
    }

    private void closeShift() {
        UiTask.load(this, () -> {
            CashShift active = services.cashShifts.visibleActiveShift();
            boolean handover = active != null && active.maNV() != Session.current().maNV()
                    && Session.can(Permission.VIEW_REPORTS);
            if (active != null && active.maNV() != Session.current().maNV() && !handover) return null;
            return active == null ? null : new Object[]{active, services.cashShifts.summary(active.maCa()), handover};
        }, result -> {
            if (result == null) {
                Dialogs.info(this, "Bạn chưa có ca đang mở.");
                return;
            }
            showCloseDialog((CashShift) result[0], (CashShiftService.Summary) result[1], (Boolean) result[2]);
        });
    }

    private void correctClosedShift() {
        CashShift selected = rows.getSelected();
        if (selected == null) {
            Dialogs.info(this, "Chọn một ca đã đóng trong lịch sử trước.");
            return;
        }
        if (selected.dangMo()) {
            Dialogs.info(this, "Không thể điều chỉnh ca đang mở. Hãy ghi nhận giao dịch trong ca hoặc đóng ca trước.");
            return;
        }
        UiTask.load(this, () -> services.cashShifts.summary(selected.maCa()),
                summary -> showCorrectionDialog(selected, summary));
    }

    private void showCorrectionDialog(CashShift shift, CashShiftService.Summary summary) {
        FormDialog f = new FormDialog(this, "Điều chỉnh đối soát ca #" + shift.maCa());
        f.addWide(Theme.muted("<html>Biên bản đóng ca được giữ nguyên. Điều chỉnh này chỉ bổ sung vào kỳ vọng đã chốt và lưu người thực hiện, thời điểm, lý do.<br>"
                + "Kỳ vọng gốc: <b>" + Money.format(summary.expected()) + "</b> · Điều chỉnh trước đó: "
                + Money.format(summary.adjustments()) + " · Kỳ vọng hiện tại: <b>" + Money.format(summary.adjustedExpected())
                + "</b><br>Nhập số dương để tăng kỳ vọng, số âm để giảm kỳ vọng.</html>"));
        JTextField impact = Fields.money(null);
        JTextField reason = Fields.text("");
        f.addRow("Tác động tới kỳ vọng (VNĐ)", impact).addRow("Lý do bắt buộc", reason);
        f.setSaveText("Ghi điều chỉnh");
        String requestKey = UUID.randomUUID().toString();
        if (f.open(() -> {
            var amount = Fields.getMoney(impact, "Mức điều chỉnh");
            var why = reason.getText();
            return () -> services.cashShifts.recordClosedShiftCorrection(shift.maCa(), amount, why, requestKey);
        })) {
            reload();
            showAdjustments(shift);
        }
    }

    private void showAdjustments() {
        CashShift selected = rows.getSelected();
        if (selected == null) {
            Dialogs.info(this, "Chọn một ca trong lịch sử để xem đối soát và điều chỉnh.");
            return;
        }
        showAdjustments(selected);
    }

    private void showAdjustments(CashShift shift) {
        UiTask.load(this, () -> new Object[]{services.cashShifts.summary(shift.maCa()),
                        services.cashShifts.adjustments(shift.maCa())}, result -> {
            CashShiftService.Summary summary = (CashShiftService.Summary) result[0];
            @SuppressWarnings("unchecked") List<CashShiftAdjustment> corrections = (List<CashShiftAdjustment>) result[1];
            StringBuilder text = new StringBuilder("Ca #").append(shift.maCa())
                    .append("\nKỳ vọng gốc: ").append(Money.format(summary.expected()))
                    .append("\nĐã đếm lúc đóng: ").append(Money.format(shift.tienDem()))
                    .append("\nLệch gốc: ").append(Money.format(shift.chenhLech()))
                    .append("\nĐiều chỉnh cộng dồn: ").append(Money.format(summary.adjustments()))
                    .append("\nKỳ vọng sau điều chỉnh: ").append(Money.format(summary.adjustedExpected()))
                    .append("\nLệch sau điều chỉnh: ").append(Money.format(summary.adjustedVariance()));
            if (corrections.isEmpty()) {
                text.append("\n\nChưa có điều chỉnh sau khi đóng ca.");
            } else {
                text.append("\n\nLịch sử điều chỉnh:");
                for (CashShiftAdjustment correction : corrections) {
                    text.append("\n• ").append(correction.thoiDiem().format(DateTimeFormatter.ofPattern("dd/MM HH:mm")))
                            .append(" · ").append(correction.tenNV()).append(" · ")
                            .append(Money.format(correction.tacDongKyVong())).append(" · ").append(correction.lyDo());
                }
            }
            Dialogs.info(this, text.toString());
        });
    }

    private void showCloseDialog(CashShift active, CashShiftService.Summary summary, boolean handover) {
        FormDialog f = new FormDialog(this, (handover ? "Quản lý xác nhận bàn giao ca #" : "Đếm tiền và đóng ca #") + active.maCa());
        var t = summary.totals();
        f.addWide(Theme.muted("<html>Đầu ca: " + Money.format(active.tienDauCa())
                + "<br>Thu tiền mặt: " + Money.format(t.cashSales()) + " · Hoàn tiền mặt: " + Money.format(t.cashRefunds())
                + "<br>Thêm: " + Money.format(t.additions()) + " · Chi: " + Money.format(t.expenses())
                + " · Rút: " + Money.format(t.withdrawals()) + "</html>"));
        BigDecimal expected = summary.expected();
        JPanel reconciliation = new JPanel(new GridLayout(1, 3, 8, 0));
        reconciliation.setOpaque(false);
        JLabel expectedValue = new JLabel(Money.format(expected));
        JLabel actualValue = new JLabel(Money.format(expected));
        JLabel varianceValue = new JLabel("Khớp · 0 đ");
        reconciliation.add(closeoutMetric("Tiền kỳ vọng", expectedValue, Theme.PRIMARY_DARK));
        reconciliation.add(closeoutMetric("Tiền thực đếm", actualValue, Theme.TEXT));
        reconciliation.add(closeoutMetric("Chênh lệch", varianceValue, Theme.SUCCESS));
        f.addWide(reconciliation);
        JTextField counted = Fields.money(summary.expected());
        JTextField note = Fields.text("");
        f.addRow("Nhập tiền đã đếm (VNĐ)", counted).addRow(handover ? "Lý do bàn giao *" : "Ghi chú nếu lệch", note);
        Runnable updatePreview = () -> {
            try {
                BigDecimal actual = Fields.getMoney(counted, "Tiền kiểm đếm cuối ca");
                BigDecimal variance = actual.subtract(expected);
                actualValue.setText(Money.format(actual));
                if (variance.signum() > 0) {
                    varianceValue.setText("Thừa · +" + Money.format(variance));
                    varianceValue.setForeground(Theme.SUCCESS);
                } else if (variance.signum() < 0) {
                    varianceValue.setText("Thiếu · −" + Money.format(variance.abs()));
                    varianceValue.setForeground(Theme.DANGER);
                } else {
                    varianceValue.setText("Khớp · 0 đ");
                    varianceValue.setForeground(Theme.SUCCESS);
                }
            } catch (RuntimeException invalid) {
                actualValue.setText("—");
                varianceValue.setText("Nhập số tiền hợp lệ");
                varianceValue.setForeground(Theme.MUTED);
            }
        };
        counted.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { updatePreview.run(); }
            @Override public void removeUpdate(DocumentEvent e) { updatePreview.run(); }
            @Override public void changedUpdate(DocumentEvent e) { updatePreview.run(); }
        });
        if (handover) f.addWide(Theme.muted("Các đơn chưa thanh toán vẫn giữ nguyên nhân viên tạo; quản lý xác nhận trách nhiệm bàn giao."));
        f.setSaveText("Xác nhận đóng ca");
        if (f.open(() -> {
            var cash = Fields.getMoney(counted, "Tiền kiểm đếm cuối ca");
            var reason = note.getText();
            return () -> {
                if (handover) services.cashShifts.closeShiftForHandover(cash, reason);
                else services.cashShifts.closeShift(cash, reason);
            };
        })) reload();
    }

    private static JPanel closeoutMetric(String label, JLabel value, java.awt.Color color) {
        JPanel card = Theme.card(new BorderLayout(0, 5));
        card.add(Theme.muted(label), BorderLayout.NORTH);
        value.setFont(Theme.font(Font.BOLD, 15));
        value.setForeground(color);
        card.add(value, BorderLayout.CENTER);
        return card;
    }
}
