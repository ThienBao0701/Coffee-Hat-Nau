package cafe.view.common;

import cafe.util.BusinessException;
import cafe.util.Dates;
import cafe.util.Money;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.FlowLayout;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Small helpers for typed form input with Vietnamese validation messages. */
public final class Fields {

    private Fields() {
    }

    public static JTextField text(String value) {
        JTextField f = new JTextField(value == null ? "" : value, 20);
        f.setFont(Theme.BASE);
        Theme.input(f);
        f.setPreferredSize(new java.awt.Dimension(Math.max(f.getPreferredSize().width, 180), 36));
        f.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 40));
        return f;
    }

    /** dd/MM/yyyy text field. */
    public static JTextField date(LocalDate value) {
        JTextField f = text(Dates.format(value));
        f.setToolTipText("Định dạng dd/MM/yyyy");
        f.setColumns(8);
        return f;
    }

    public static JTextField dateTime(LocalDateTime value) {
        JTextField f = text(Dates.format(value));
        f.setToolTipText("Định dạng dd/MM/yyyy HH:mm");
        f.setColumns(11);
        return f;
    }

    public static JTextField money(BigDecimal value) {
        JTextField f = text(value == null ? "" : Money.plain(value));
        f.setHorizontalAlignment(JTextField.RIGHT);
        f.setToolTipText("Số tiền (VNĐ), ví dụ 25000");
        return f;
    }

    public static JTextField decimal(BigDecimal value) {
        JTextField f = text(value == null ? "" : value.stripTrailingZeros().toPlainString());
        f.setHorizontalAlignment(JTextField.RIGHT);
        return f;
    }

    public static JCheckBox check(String label, boolean value) {
        JCheckBox c = new JCheckBox(label, value);
        c.setOpaque(false);
        return c;
    }

    public static <E> JComboBox<E> combo(List<E> items, E selected) {
        JComboBox<E> c = new JComboBox<>();
        items.forEach(c::addItem);
        if (selected != null) {
            c.setSelectedItem(selected);
        }
        c.setFont(Theme.BASE);
        Theme.input(c);
        c.setPreferredSize(new java.awt.Dimension(Math.max(c.getPreferredSize().width, 160), 36));
        return c;
    }

    public static LocalDate getDate(JTextField f, String label) {
        return Dates.parseDate(f.getText(), label);
    }

    public static LocalDateTime getDateTime(JTextField f, String label) {
        return Dates.parseDateTime(f.getText(), label);
    }

    public static BigDecimal getMoney(JTextField f, String label) {
        return Money.parse(f.getText(), label);
    }

    public static BigDecimal getDecimal(JTextField f, String label) {
        String t = f.getText().trim().replace(',', '.');
        if (t.isEmpty()) {
            return null;
        }
        BigDecimal value;
        try {
            value = new BigDecimal(t);
        } catch (NumberFormatException e) {
            throw new BusinessException(label + " phải là số hợp lệ.");
        }
        if (value.abs().compareTo(new BigDecimal("10000000000")) > 0 || value.scale() > 3) {   // DECIMAL(14,3)
            throw new BusinessException(label + " quá lớn hoặc có quá 3 chữ số thập phân.");
        }
        return value;
    }

    public static int getInt(JTextField f, String label) {
        try {
            return Integer.parseInt(f.getText().trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(label + " phải là số nguyên.");
        }
    }

    /** Search box that calls back on every keystroke. */
    public static JTextField search(java.util.function.Consumer<String> onChange) {
        JTextField f = text("");
        f.setColumns(18);
        f.setToolTipText("Tìm kiếm (không cần gõ dấu)");
        f.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                onChange.accept(f.getText());
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                onChange.accept(f.getText());
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                onChange.accept(f.getText());
            }
        });
        return f;
    }

    /** Horizontal toolbar row. */
    public static JPanel row(java.awt.Component... items) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder());
        for (java.awt.Component c : items) {
            p.add(c);
        }
        return p;
    }
}
