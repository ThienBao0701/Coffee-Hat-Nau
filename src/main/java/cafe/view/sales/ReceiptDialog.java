package cafe.view.sales;

import cafe.config.AppConfig;
import cafe.model.InvoiceDetail;
import cafe.view.common.Dialogs;
import cafe.view.common.Theme;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.print.PrinterException;

/** Receipt preview with a Print button (uses the operating system's print dialog). */
public final class ReceiptDialog {

    private ReceiptDialog() {
    }

    public static void show(Component owner, InvoiceDetail invoice, AppConfig cfg) {
        JDialog d = new JDialog(SwingUtilities.getWindowAncestor(owner), "Hóa đơn " + invoice.hoaDon().maHD(),
                JDialog.ModalityType.APPLICATION_MODAL);
        JTextArea text = new JTextArea(ReceiptFormatter.format(invoice, cfg.get("shop.name"), cfg.get("shop.address"), cfg.get("shop.phone")));
        text.setEditable(false);
        text.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        text.setBorder(Theme.pad(12, 16));
        JScrollPane scroll = new JScrollPane(text);
        Theme.styleScrollPane(scroll);
        scroll.setPreferredSize(new Dimension(460, 560));
        JButton print = Theme.primary("In hóa đơn");
        JButton close = Theme.button("Đóng");
        print.addActionListener(e -> {
            try {
                text.print();
            } catch (PrinterException ex) {
                Dialogs.info(d, "Không thể in hóa đơn: hãy kiểm tra máy in đã được cài đặt và bật.");
            }
        });
        close.addActionListener(e -> d.dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 10));
        buttons.add(close);
        buttons.add(print);
        d.getContentPane().add(scroll, BorderLayout.CENTER);
        d.getContentPane().add(buttons, BorderLayout.SOUTH);
        d.pack();
        d.setLocationRelativeTo(owner);
        d.setVisible(true);
    }
}
