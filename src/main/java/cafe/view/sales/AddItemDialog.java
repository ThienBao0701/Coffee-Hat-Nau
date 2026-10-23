package cafe.view.sales;

import cafe.model.ChiTietHoaDon;
import cafe.model.ChiTietTopping;
import cafe.model.MenuItem;
import cafe.model.Topping;
import cafe.service.OrderService;
import cafe.util.Money;
import cafe.view.common.Theme;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.KeyEvent;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Choose size, quantity, toppings and a preparation note for one drink line. */
public class AddItemDialog extends JDialog {

    /** What the user picked. toppings: topping id -> quantity per drink. */
    public record Choice(int maSize, int soLuong, Map<Integer, Integer> toppings, String ghiChu) {
    }

    private final List<JRadioButton> sizeButtons = new ArrayList<>();
    private final List<MenuItem.SizePrice> prices;
    private final JSpinner qty = new JSpinner(new SpinnerNumberModel(1, 1, OrderService.MAX_QTY, 1));
    private final Map<Topping, JCheckBox> toppingChecks = new LinkedHashMap<>();
    private final Map<Topping, JSpinner> toppingQty = new LinkedHashMap<>();
    private final JTextField note = new JTextField(24);
    private final JLabel total = new JLabel();
    private Choice result;

    /** @param existing line being edited (size is then fixed), or null to add a new line */
    public AddItemDialog(Component owner, MenuItem item, List<Topping> toppings, ChiTietHoaDon existing) {
        super(SwingUtilities.getWindowAncestor(owner), existing == null ? "Thêm món" : "Sửa món", ModalityType.APPLICATION_MODAL);
        this.prices = item.gia();
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(Theme.pad(14, 16));
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0;
        g.gridy = 0;
        g.gridwidth = 2;
        g.anchor = GridBagConstraints.WEST;
        g.insets = new Insets(4, 0, 8, 0);
        JLabel name = new JLabel(item.mon().tenMon());
        name.setFont(Theme.font(Font.BOLD, 16));
        form.add(name, g);

        JPanel sizes = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        ButtonGroup group = new ButtonGroup();
        for (MenuItem.SizePrice sp : prices) {
            JRadioButton r = new JRadioButton(sp.size().tenSize() + " - " + Money.format(sp.giaBan()) + "   ");
            r.setOpaque(false);
            r.addActionListener(e -> updateTotal());
            group.add(r);
            sizes.add(r);
            sizeButtons.add(r);
            boolean pick = existing != null ? sp.size().maSize() == existing.maSize() : sp.size().tenSize().equals("M");
            r.setSelected(pick);
            r.setEnabled(existing == null);
        }
        if (group.getSelection() == null && !sizeButtons.isEmpty()) {
            sizeButtons.get(0).setSelected(true);
        }
        row(form, 1, "Size", sizes);
        if (existing != null) {
            qty.setValue(existing.soLuong());
            note.setText(existing.ghiChu() == null ? "" : existing.ghiChu());
        }
        qty.addChangeListener(e -> updateTotal());
        row(form, 2, "Số lượng", qty);

        JPanel tp = new JPanel(new GridBagLayout());
        int r = 0;
        for (Topping t : toppings) {
            JCheckBox cb = new JCheckBox(t.tenTopping() + " (+" + Money.format(t.giaThem()) + ")");
            cb.setOpaque(false);
            JSpinner sp = new JSpinner(new SpinnerNumberModel(1, 1, OrderService.MAX_TOPPING_QTY, 1));
            sp.setEnabled(false);
            if (existing != null) {
                for (ChiTietTopping ct : existing.toppings()) {
                    if (ct.maTopping() == t.maTopping()) {
                        cb.setSelected(true);
                        sp.setEnabled(true);
                        sp.setValue(ct.soLuong());
                    }
                }
            }
            cb.addActionListener(e -> {
                sp.setEnabled(cb.isSelected());
                updateTotal();
            });
            sp.addChangeListener(e -> updateTotal());
            toppingChecks.put(t, cb);
            toppingQty.put(t, sp);
            GridBagConstraints c1 = new GridBagConstraints();
            c1.gridx = 0;
            c1.gridy = r;
            c1.anchor = GridBagConstraints.WEST;
            tp.add(cb, c1);
            GridBagConstraints c2 = new GridBagConstraints();
            c2.gridx = 1;
            c2.gridy = r++;
            c2.insets = new Insets(1, 8, 1, 0);
            tp.add(sp, c2);
        }
        if (toppings.isEmpty()) {
            tp.add(Theme.muted("Không có topping"));
        }
        row(form, 3, "Topping", tp);
        row(form, 4, "Ghi chú", note);
        note.setToolTipText("Ví dụ: ít đá, ít ngọt");
        total.setFont(Theme.font(Font.BOLD, 15));
        row(form, 5, "Thành tiền", total);

        JButton ok = Theme.primary(existing == null ? "Thêm vào đơn" : "Cập nhật");
        JButton cancel = Theme.button("Hủy");
        ok.addActionListener(e -> {
            result = new Choice(selectedSize().size().maSize(), (Integer) qty.getValue(), selectedToppings(),
                    note.getText().isBlank() ? null : note.getText().trim());
            dispose();
        });
        cancel.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 10));
        buttons.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER));
        buttons.add(cancel);
        buttons.add(ok);
        getContentPane().add(form, BorderLayout.CENTER);
        getContentPane().add(buttons, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(ok);
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        updateTotal();
        pack();
        setLocationRelativeTo(owner);
    }

    private static void row(JPanel form, int y, String label, Component c) {
        GridBagConstraints l = new GridBagConstraints();
        l.gridx = 0;
        l.gridy = y;
        l.anchor = GridBagConstraints.NORTHWEST;
        l.insets = new Insets(6, 0, 6, 14);
        form.add(new JLabel(label), l);
        GridBagConstraints f = new GridBagConstraints();
        f.gridx = 1;
        f.gridy = y;
        f.anchor = GridBagConstraints.WEST;
        f.insets = new Insets(4, 0, 4, 0);
        form.add(c, f);
    }

    private MenuItem.SizePrice selectedSize() {
        for (int i = 0; i < sizeButtons.size(); i++) {
            if (sizeButtons.get(i).isSelected()) {
                return prices.get(i);
            }
        }
        return prices.get(0);
    }

    private Map<Integer, Integer> selectedToppings() {
        Map<Integer, Integer> m = new LinkedHashMap<>();
        toppingChecks.forEach((t, cb) -> {
            if (cb.isSelected()) {
                m.put(t.maTopping(), (Integer) toppingQty.get(t).getValue());
            }
        });
        return m;
    }

    /** Preview only; the service recalculates from the price list when saving. */
    private void updateTotal() {
        BigDecimal perDrink = selectedSize().giaBan();
        for (Map.Entry<Topping, JCheckBox> e : toppingChecks.entrySet()) {
            if (e.getValue().isSelected()) {
                perDrink = perDrink.add(e.getKey().giaThem().multiply(BigDecimal.valueOf((Integer) toppingQty.get(e.getKey()).getValue())));
            }
        }
        total.setText(Money.format(perDrink.multiply(BigDecimal.valueOf((Integer) qty.getValue()))));
    }

    /** Shows the dialog; returns the choice or null if cancelled. */
    public Choice ask() {
        setVisible(true);
        return result;
    }
}
