package cafe.view.sales;

import cafe.model.InvoiceDetail;
import cafe.model.KhachHang;
import cafe.model.KhuyenMai;
import cafe.model.PhuongThucThanhToan;
import cafe.service.OrderService;
import cafe.service.PriceCalculator;
import cafe.service.Services;
import cafe.util.BusinessException;
import cafe.util.Money;
import cafe.view.common.Dialogs;
import cafe.view.common.Fields;
import cafe.view.common.Theme;
import cafe.view.common.UiTask;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.KeyEvent;
import java.math.BigDecimal;

/**
 * Checkout: optional promotion and customer, payment method, cash received and change.
 * Totals shown here come from OrderService.preview (same rules as the actual payment).
 * The confirm button is disabled on the first click, and the service refuses a second payment anyway.
 */
public class PaymentDialog extends JDialog {

    private static final String NO_PROMO = "(Không áp dụng)";

    private final Services services;
    private final InvoiceDetail invoice;
    private final JComboBox<Object> promo = new JComboBox<>();
    private final JComboBox<PhuongThucThanhToan> method = new JComboBox<>(PhuongThucThanhToan.values());
    private final JTextField phone = Fields.text("");
    private final JLabel customerLabel = Theme.muted("Khách lẻ");
    private final JTextField cash = Fields.money(null);
    private final JLabel subtotal = new JLabel();
    private final JLabel discount = new JLabel();
    private final JLabel tax = new JLabel();
    private final JLabel total = new JLabel();
    private final JLabel change = new JLabel();
    private final JLabel error = new JLabel(" ");
    private final JButton confirm = Theme.primary("Xác nhận thanh toán");
    private PriceCalculator.Totals totals;
    private Integer customerId;
    private InvoiceDetail paid;
    private boolean paying;   // a late preview must not re-enable the button while payment runs

    public PaymentDialog(Component owner, Services services, InvoiceDetail invoice) {
        super(SwingUtilities.getWindowAncestor(owner), "Thanh toán - " + invoice.hoaDon().nhan(), ModalityType.APPLICATION_MODAL);
        this.services = services;
        this.invoice = invoice;
        if (invoice.hoaDon().maKH() != null) {
            customerId = invoice.hoaDon().maKH();
            customerLabel.setText(invoice.hoaDon().tenKH());
        }

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(Theme.pad(14, 18));
        int y = 0;
        promo.addItem(NO_PROMO);
        promo.addActionListener(e -> recalc());
        row(form, y++, "Khuyến mãi", promo);
        JButton find = Theme.button("Tìm");
        find.addActionListener(e -> findCustomer());
        JPanel cust = new JPanel(new BorderLayout(6, 0));
        cust.add(phone, BorderLayout.CENTER);
        cust.add(find, BorderLayout.EAST);
        phone.setToolTipText("Nhập số điện thoại khách (không bắt buộc)");
        row(form, y++, "SĐT khách hàng", cust);
        row(form, y++, "", customerLabel);
        row(form, y++, "Phương thức", method);
        row(form, y++, "Khách đưa", cash);

        JPanel sum = Theme.card(new GridBagLayout());
        int sy = 0;
        row(sum, sy++, "Tạm tính", subtotal);
        row(sum, sy++, "Giảm giá", discount);
        row(sum, sy++, "Thuế", tax);
        total.setFont(Theme.font(Font.BOLD, 20));
        total.setForeground(Theme.PRIMARY);
        row(sum, sy++, "Tổng thanh toán", total);
        row(sum, sy, "Tiền thối", change);
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0;
        g.gridy = y++;
        g.gridwidth = 2;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.insets = new Insets(12, 0, 0, 0);
        form.add(sum, g);

        method.addActionListener(e -> {
            boolean isCash = method.getSelectedItem() == PhuongThucThanhToan.TIEN_MAT;
            cash.setEnabled(isCash);
            updateChange();
        });
        cash.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateChange();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateChange();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateChange();
            }
        });

        error.setForeground(Theme.DANGER);
        error.setBorder(BorderFactory.createEmptyBorder(0, 18, 0, 18));
        JButton cancel = Theme.button("Hủy");
        cancel.addActionListener(e -> dispose());
        confirm.addActionListener(e -> pay());
        confirm.setEnabled(false);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 10));
        buttons.add(cancel);
        buttons.add(confirm);
        JPanel south = new JPanel(new BorderLayout());
        south.add(error, BorderLayout.NORTH);
        south.add(buttons, BorderLayout.SOUTH);
        getContentPane().add(form, BorderLayout.CENTER);
        getContentPane().add(south, BorderLayout.SOUTH);
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        pack();
        setSize(Math.max(getWidth(), 460), getHeight());
        setLocationRelativeTo(owner);

        BigDecimal sub = invoice.tamTinh();
        UiTask.load(this, () -> services.promotions.findEligible(sub), list -> {
            list.forEach(promo::addItem);
            recalc();
        });
    }

    private static void row(JPanel p, int y, String label, Component c) {
        GridBagConstraints l = new GridBagConstraints();
        l.gridx = 0;
        l.gridy = y;
        l.anchor = GridBagConstraints.WEST;
        l.insets = new Insets(4, 0, 4, 16);
        p.add(new JLabel(label), l);
        GridBagConstraints f = new GridBagConstraints();
        f.gridx = 1;
        f.gridy = y;
        f.fill = GridBagConstraints.HORIZONTAL;
        f.weightx = 1;
        f.insets = new Insets(4, 0, 4, 0);
        p.add(c, f);
    }

    private Integer selectedPromo() {
        return promo.getSelectedItem() instanceof KhuyenMai k ? k.maKM() : null;
    }

    private void recalc() {
        confirm.setEnabled(false);
        Integer km = selectedPromo();
        UiTask.load(this, () -> services.orders.preview(invoice.hoaDon().maHD(), km), t -> {
            totals = t;
            subtotal.setText(Money.format(t.tamTinh()));
            discount.setText(t.tienGiam().signum() == 0 ? "0 đ" : "-" + Money.format(t.tienGiam()));
            tax.setText(Money.format(t.tienThue()));
            total.setText(Money.format(t.tongTien()));
            if (cash.getText().isBlank()) {
                cash.setText(Money.plain(t.tongTien()));
            }
            error.setText(" ");
            confirm.setEnabled(!paying);
            updateChange();
        }, ex -> error.setText(Dialogs.userMessage(ex)));
    }

    private void updateChange() {
        if (totals == null) {
            return;
        }
        if (method.getSelectedItem() != PhuongThucThanhToan.TIEN_MAT) {
            change.setText("0 đ");
            return;
        }
        try {
            BigDecimal given = Money.parse(cash.getText(), "Tiền khách đưa");
            BigDecimal diff = given == null ? null : given.subtract(totals.tongTien());
            change.setText(diff == null ? "" : diff.signum() < 0 ? "Còn thiếu " + Money.format(diff.negate()) : Money.format(diff));
            change.setForeground(diff != null && diff.signum() < 0 ? Theme.DANGER : Theme.TEXT);
        } catch (BusinessException e) {
            change.setText("Số tiền không hợp lệ");
            change.setForeground(Theme.DANGER);
        }
    }

    private void findCustomer() {
        String p = phone.getText();
        if (p.isBlank()) {
            customerId = null;
            customerLabel.setText("Khách lẻ");
            return;
        }
        UiTask.load(this, () -> services.customers.findByPhone(p), k -> {
            if (k != null) {
                customerId = k.maKH();
                customerLabel.setText(k.tenKH());
                return;
            }
            String name = JOptionPane.showInputDialog(this, "Chưa có khách hàng với số " + p + ".\nNhập tên để thêm khách hàng mới:",
                    "Khách hàng mới", JOptionPane.QUESTION_MESSAGE);
            if (name == null || name.isBlank()) {
                return;
            }
            UiTask.load(this, () -> services.customers.save(new KhachHang(null, name, null, null, p, null)), id -> {
                customerId = id;
                customerLabel.setText(name.trim());
            });
        });
    }

    private void pay() {
        PhuongThucThanhToan pt = (PhuongThucThanhToan) method.getSelectedItem();
        BigDecimal given;
        try {
            given = pt == PhuongThucThanhToan.TIEN_MAT ? Money.parse(cash.getText(), "Tiền khách đưa") : null;
        } catch (BusinessException e) {
            error.setText(e.getMessage());
            return;
        }
        paying = true;
        confirm.setEnabled(false);   // no double payment from double clicks
        OrderService.PaymentRequest req = new OrderService.PaymentRequest(invoice.hoaDon().maHD(), selectedPromo(), customerId, pt, given);
        UiTask.load(this, () -> services.orders.pay(req), result -> {
            paid = result;
            dispose();
        }, ex -> {
            paying = false;
            error.setText(Dialogs.userMessage(ex));
            confirm.setEnabled(true);
        });
    }

    /** Shows the dialog; returns the paid invoice or null if not paid. */
    public InvoiceDetail ask() {
        setVisible(true);
        return paid;
    }
}
