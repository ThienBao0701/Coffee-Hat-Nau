package cafe.view.common;

import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.UIManager;
import javax.swing.border.AbstractBorder;
import javax.swing.border.Border;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.FontUIResource;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Shared brand tokens and polished Swing component factories. */
public final class Theme {

    private static final Logger LOG = Logger.getLogger(Theme.class.getName());

    // Modern cafe palette: charcoal shell, clear neutral work surfaces, burnt-orange action.
    public static final Color BG = new Color(0xF5F4F0);
    public static final Color SURFACE = Color.WHITE;
    public static final Color DARK = new Color(0x191C22);
    public static final Color DARK_2 = new Color(0x242932);
    public static final Color DARK_HOVER = new Color(0x303741);
    public static final Color PRIMARY = new Color(0xB95F37);
    public static final Color PRIMARY_DARK = new Color(0x984A29);
    public static final Color ACCENT = new Color(0xD77745);
    public static final Color ACCENT_SOFT = new Color(0xFCEDE5);
    public static final Color FOREST = new Color(0x52745D);
    public static final Color TEXT = new Color(0x20242B);
    public static final Color MUTED = new Color(0x747984);
    public static final Color BORDER = new Color(0xE3E5E8);
    public static final Color SELECTION = new Color(0xFCEDE5);
    public static final Color ROW_ALT = new Color(0xFAFAF8);
    public static final Color DANGER = new Color(0xBB4D4D);
    public static final Color SUCCESS = new Color(0x2F7859);
    public static final Color WARNING = new Color(0xB7812D);
    public static final Color WARNING_BG = new Color(0xFBF3E4);
    public static final Color WARNING_TEXT = new Color(0x79500F);
    public static final Color INFO = new Color(0x4F7186);
    public static final Color DISABLED = new Color(0xC4C9D0);

    public static final int S4 = 4;
    public static final int S8 = 8;
    public static final int S12 = 12;
    public static final int S16 = 16;
    public static final int S20 = 20;
    public static final int S24 = 24;

    private static final String FAMILY = pickFamily();
    public static final Font BASE = font(Font.PLAIN, 14);
    public static final Font BOLD = font(Font.BOLD, 14);
    public static final Font SMALL = font(Font.PLAIN, 12);
    public static final Font PAGE_TITLE = font(Font.BOLD, 26);
    public static final Font SECTION_TITLE = font(Font.BOLD, 17);
    public static final Font MONO = new Font(Font.MONOSPACED, Font.PLAIN, 13);

    private Theme() {
    }

    private static String pickFamily() {
        if (GraphicsEnvironment.isHeadless()) {
            return Font.DIALOG;
        }
        String[] available = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        for (String family : new String[]{"Segoe UI", "Arial", "Tahoma"}) {
            if (Arrays.asList(available).contains(family)) {
                return family;
            }
        }
        return Font.DIALOG;
    }

    public static Font font(int style, float size) {
        return new Font(FAMILY, style, Math.round(size));
    }

    /** Installs FlatLaf and the app palette before any application window is constructed. */
    public static void install() {
        try {
            if (!FlatLightLaf.setup()) {
                throw new IllegalStateException("FlatLaf setup returned false");
            }
            installDefaults();
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Cannot install FlatLaf; trying the platform Look and Feel", e);
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                installDefaults();
            } catch (Exception fallback) {
                LOG.log(Level.SEVERE, "Cannot install platform Look and Feel", fallback);
            }
        }
    }

    private static void installDefaults() {
        FontUIResource base = new FontUIResource(BASE);
        FontUIResource bodyBold = new FontUIResource(BOLD);
        UIManager.put("defaultFont", base);
        for (String key : new String[]{"Component.font", "Label.font", "Button.font", "ToggleButton.font",
                "TextField.font", "PasswordField.font", "FormattedTextField.font", "TextArea.font",
                "ComboBox.font", "Spinner.font", "CheckBox.font", "RadioButton.font", "List.font",
                "Table.font", "TableHeader.font", "TabbedPane.font", "OptionPane.messageFont",
                "OptionPane.buttonFont", "Menu.font", "MenuItem.font", "ToolTip.font"}) {
            UIManager.put(key, base);
        }
        UIManager.put("Button.font", bodyBold);
        UIManager.put("TableHeader.font", new FontUIResource(font(Font.BOLD, 13)));
        UIManager.put("Panel.background", color(BG));
        UIManager.put("Component.background", color(SURFACE));
        UIManager.put("Component.foreground", color(TEXT));
        UIManager.put("Label.foreground", color(TEXT));
        UIManager.put("RootPane.background", color(BG));
        UIManager.put("Viewport.background", color(SURFACE));
        UIManager.put("Component.arc", 10);
        UIManager.put("Button.arc", 10);
        UIManager.put("TextComponent.arc", 9);
        UIManager.put("ComboBox.arc", 9);
        UIManager.put("Spinner.arc", 9);
        UIManager.put("Component.focusColor", color(ACCENT));
        UIManager.put("Component.borderColor", color(BORDER));
        UIManager.put("Component.focusWidth", 1);
        UIManager.put("Component.innerFocusWidth", 1);
        UIManager.put("Button.margin", new Insets(8, 14, 8, 14));
        UIManager.put("Button.default.background", color(PRIMARY));
        UIManager.put("Button.default.foreground", Color.WHITE);
        UIManager.put("Button.default.hoverBackground", color(PRIMARY_DARK));
        UIManager.put("Button.hoverBackground", color(new Color(0xF1F2F4)));
        UIManager.put("TextField.background", color(SURFACE));
        UIManager.put("PasswordField.background", color(SURFACE));
        UIManager.put("TextArea.background", color(SURFACE));
        UIManager.put("TextComponent.selectionBackground", color(SELECTION));
        UIManager.put("TextComponent.selectionForeground", color(TEXT));
        UIManager.put("ComboBox.background", color(SURFACE));
        UIManager.put("ComboBox.selectionBackground", color(SELECTION));
        UIManager.put("ComboBox.selectionForeground", color(TEXT));
        UIManager.put("Table.background", color(SURFACE));
        UIManager.put("Table.foreground", color(TEXT));
        UIManager.put("Table.alternateRowColor", color(ROW_ALT));
        UIManager.put("Table.selectionBackground", color(SELECTION));
        UIManager.put("Table.selectionForeground", color(TEXT));
        UIManager.put("Table.gridColor", color(BORDER));
        UIManager.put("TableHeader.background", color(new Color(0xF1F2F4)));
        UIManager.put("TableHeader.foreground", color(new Color(0x555B64)));
        UIManager.put("TableHeader.height", 42);
        UIManager.put("Table.rowHeight", 42);
        UIManager.put("TabbedPane.background", color(BG));
        UIManager.put("TabbedPane.selectedBackground", color(SURFACE));
        UIManager.put("TabbedPane.selectedForeground", color(TEXT));
        UIManager.put("TabbedPane.underlineColor", color(PRIMARY));
        UIManager.put("TabbedPane.tabHeight", 40);
        UIManager.put("TabbedPane.showTabSeparators", false);
        UIManager.put("ScrollBar.width", 12);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.trackArc", 999);
        UIManager.put("ScrollBar.thumbInsets", new Insets(2, 3, 2, 3));
        UIManager.put("ScrollBar.track", color(BG));
        UIManager.put("ScrollBar.thumb", color(new Color(0xC4C7CC)));
        UIManager.put("OptionPane.background", color(SURFACE));
        UIManager.put("OptionPane.messageForeground", color(TEXT));
        UIManager.put("OptionPane.yesButtonText", "Đồng ý");
        UIManager.put("OptionPane.noButtonText", "Không");
        UIManager.put("OptionPane.cancelButtonText", "Hủy");
        UIManager.put("OptionPane.okButtonText", "OK");
        UIManager.put("ToolTip.background", color(DARK));
        UIManager.put("ToolTip.foreground", Color.WHITE);
        UIManager.put("Separator.foreground", color(BORDER));
    }

    private static ColorUIResource color(Color value) {
        return new ColorUIResource(value);
    }

    /** Uses FlatLaf's rounded outline treatment while retaining the native control border. */
    public static void input(JComponent component) {
        if (Boolean.TRUE.equals(component.getClientProperty("cafe.inputStyled"))) return;
        component.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusGained(java.awt.event.FocusEvent e) {
                component.putClientProperty("JComponent.outline", "accent");
            }
            @Override public void focusLost(java.awt.event.FocusEvent e) {
                component.putClientProperty("JComponent.outline", null);
            }
        });
        component.putClientProperty("cafe.inputStyled", Boolean.TRUE);
    }

    /** Leaves scroll painting to FlatLaf and standardizes scroll response and borders. */
    public static void styleScrollPane(JScrollPane pane) {
        pane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        pane.getVerticalScrollBar().setUnitIncrement(20);
        pane.getHorizontalScrollBar().setUnitIncrement(20);
        pane.setBorder(BorderFactory.createEmptyBorder());
    }

    private static Border roundedLine(Color color, int arc) {
        return new AbstractBorder() {
            @Override
            public Insets getBorderInsets(Component c) {
                return new Insets(1, 1, 1, 1);
            }

            @Override
            public Insets getBorderInsets(Component c, Insets insets) {
                insets.set(1, 1, 1, 1);
                return insets;
            }

            @Override
            public void paintBorder(Component c, Graphics graphics, int x, int y, int width, int height) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(color);
                g.draw(new RoundRectangle2D.Float(x + .5f, y + .5f, width - 1f, height - 1f, arc, arc));
                g.dispose();
            }
        };
    }

    private static JButton base(String text) {
        JButton button = new JButton(text);
        button.setFont(BOLD);
        button.putClientProperty("JButton.buttonType", "roundRect");
        button.setFocusPainted(true);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    /** Secondary action. */
    public static JButton button(String text) {
        JButton button = base(text);
        button.setBackground(SURFACE);
        button.setForeground(TEXT);
        return button;
    }

    /** Primary action of a screen or dialog. */
    public static JButton primary(String text) {
        JButton button = base(text);
        button.setBackground(PRIMARY);
        button.setForeground(Color.WHITE);
        button.putClientProperty("JButton.defaultButton", Boolean.TRUE);
        return button;
    }

    /** Destructive action, still secondary in visual weight until intentionally confirmed. */
    public static JButton danger(String text) {
        JButton button = base(text);
        button.setBackground(SURFACE);
        button.setForeground(DANGER);
        button.putClientProperty("JComponent.outline", "error");
        return button;
    }

    /** Clickable product tile used by POS; the whole tile opens size and topping selection. */
    public static JButton productTile(String accessibleName) {
        JButton button = base(accessibleName);
        button.setHorizontalAlignment(JButton.LEFT);
        button.setVerticalAlignment(JButton.TOP);
        button.setBackground(SURFACE);
        button.setForeground(TEXT);
        button.setBorder(BorderFactory.createCompoundBorder(roundedLine(BORDER, 14),
                BorderFactory.createEmptyBorder(12, 14, 12, 14)));
        button.setPreferredSize(new Dimension(210, 84));
        return button;
    }

    public static JLabel title(String text) {
        JLabel label = new JLabel(text);
        label.setFont(PAGE_TITLE);
        label.setForeground(TEXT);
        return label;
    }

    public static JLabel sectionTitle(String text) {
        JLabel label = new JLabel(text);
        label.setFont(SECTION_TITLE);
        label.setForeground(TEXT);
        return label;
    }

    public static JLabel muted(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(MUTED);
        label.setFont(SMALL);
        return label;
    }

    public static JLabel badge(String text, Color foreground, Color background) {
        JLabel label = new JLabel("  " + text + "  ");
        label.setOpaque(true);
        label.setForeground(foreground);
        label.setBackground(background);
        label.setFont(font(Font.BOLD, 12));
        label.setBorder(BorderFactory.createEmptyBorder(4, 5, 4, 5));
        return label;
    }

    public static Border pad(int vertical, int horizontal) {
        return BorderFactory.createEmptyBorder(vertical, horizontal, vertical, horizontal);
    }

    /** Rounded white surface reserved for summaries and focused working groups. */
    public static JPanel card(LayoutManager layout) {
        JPanel panel = new JPanel(layout) {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(getBackground());
                g.fill(new RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 1, 16, 16));
                g.dispose();
            }
        };
        panel.setOpaque(false);
        panel.setBackground(SURFACE);
        panel.setBorder(BorderFactory.createCompoundBorder(roundedLine(BORDER, 16),
                BorderFactory.createEmptyBorder(12, 14, 12, 14)));
        return panel;
    }

    /** Common page scaffold, with a stronger title hierarchy and deliberate canvas spacing. */
    public static JPanel screen(String title, Component content) {
        JPanel panel = new JPanel(new BorderLayout(0, S20));
        panel.setBackground(BG);
        panel.setBorder(pad(S20, S24));
        panel.add(title(title), BorderLayout.NORTH);
        panel.add(content, BorderLayout.CENTER);
        return panel;
    }
}
