package cafe.view.common;


import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.util.function.Supplier;

/**
 * Modal add/edit form: label + field rows, inline error line, Save / Cancel.
 * Usage:
 * <pre>
 *   FormDialog f = new FormDialog(this, "Thêm món");
 *   f.addRow("Tên món *", txtTen);
 *   boolean saved = f.open(() -> {
 *       Mon m = new Mon(...txtTen.getText()...);   // runs on the UI thread: read fields, validate
 *       return () -> service.saveMon(m);           // runs in the background: database work
 *   });
 * </pre>
 */
public class FormDialog extends JDialog {

    private final JPanel form = new JPanel(new GridBagLayout());
    private final JScrollPane scroll = new JScrollPane(form);
    private final JLabel error = new JLabel(" ");
    private final JButton save = Theme.primary("Lưu");
    private final JButton cancel = Theme.button("Hủy");
    private int row;
    private boolean saved;

    public FormDialog(Component parent, String title) {
        super(parent instanceof Window w ? w : SwingUtilities.getWindowAncestor(parent), title, ModalityType.APPLICATION_MODAL);
        form.setBorder(Theme.pad(14, 16));
        form.setBackground(Theme.BG);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        Theme.styleScrollPane(scroll);
        scroll.getViewport().setBackground(Theme.BG);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        error.setForeground(Theme.DANGER);
        error.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 16));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 10));
        buttons.add(cancel);
        buttons.add(save);
        JPanel south = new JPanel(new BorderLayout());
        south.add(error, BorderLayout.NORTH);
        south.add(buttons, BorderLayout.SOUTH);
        getContentPane().add(scroll, BorderLayout.CENTER);
        getContentPane().add(south, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(save);
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        cancel.addActionListener(e -> dispose());
    }

    public FormDialog addRow(String label, Component field) {
        GridBagConstraints l = new GridBagConstraints();
        l.gridx = 0;
        l.gridy = row;
        l.anchor = GridBagConstraints.WEST;
        l.insets = new Insets(4, 0, 4, 12);
        form.add(new JLabel(label), l);
        GridBagConstraints f = new GridBagConstraints();
        f.gridx = 1;
        f.gridy = row++;
        f.fill = GridBagConstraints.HORIZONTAL;
        f.weightx = 1;
        f.insets = new Insets(4, 0, 4, 0);
        if (field instanceof JComponent jc) {
            if (field instanceof JTextComponent || field instanceof javax.swing.JComboBox<?>) Theme.input(jc);
            if (jc.getPreferredSize().width < 260) {
                jc.setPreferredSize(new Dimension(260, Math.max(jc.getPreferredSize().height, 32)));
            }
        }
        form.add(field, f);
        return this;
    }

    /** A full-width row (e.g. a hint or a table). */
    public FormDialog addWide(Component c) {
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0;
        g.gridy = row++;
        g.gridwidth = 2;
        g.fill = GridBagConstraints.BOTH;
        g.weightx = 1;
        g.weighty = 0;
        g.insets = new Insets(6, 0, 4, 0);
        form.add(c, g);
        return this;
    }

    public void setSaveText(String text) {
        save.setText(text);
    }

    /**
     * Shows the dialog. On Save, {@code action} runs on the UI thread (read and validate the fields)
     * and returns the database work, which runs in the background. Errors are shown inline and the
     * dialog stays open; on success it closes and this method returns true.
     */
    public boolean open(Supplier<Runnable> action) {
        save.addActionListener(e -> {
            Runnable work;
            try {
                work = action.get();
            } catch (RuntimeException ex) {
                showError(Dialogs.userMessage(ex));
                return;
            }
            save.setEnabled(false);
            cancel.setEnabled(false);
            UiTask.load(this, () -> {
                work.run();
                return Boolean.TRUE;
            }, ok -> {
                saved = true;
                dispose();
            }, ex -> {
                save.setEnabled(true);
                cancel.setEnabled(true);
                showError(Dialogs.userMessage(ex));
            });
        });
        pack();
        Dimension minimum = new Dimension(460, 220);
        if (!GraphicsEnvironment.isHeadless()) {
            Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
            int maxWidth = Math.max(460, Math.min(900, screen.width - 80));
            int maxHeight = Math.max(360, Math.min(650, screen.height - 100));
            if (getWidth() > maxWidth || getHeight() > maxHeight) {
                scroll.setPreferredSize(new Dimension(Math.min(maxWidth - 30, Math.max(500, getWidth() - 20)),
                        Math.min(maxHeight - 110, Math.max(300, getHeight() - 110))));
                pack();
            }
            minimum = new Dimension(Math.min(maxWidth, Math.max(minimum.width, getWidth())),
                    Math.min(maxHeight, Math.max(minimum.height, getHeight())));
        }
        setMinimumSize(minimum);
        setLocationRelativeTo(getOwner());
        setVisible(true);
        return saved;
    }

    private void showError(String message) {
        error.setText("<html>" + message.replace("&", "&amp;").replace("<", "&lt;") + "</html>");
        pack();
    }
}
