package cafe.view;

import cafe.controller.AppController;
import cafe.view.common.Theme;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;

/** Login window. All checks happen in AuthService; this screen only collects input. */
public class LoginFrame extends JFrame {

    private static final int NORMAL_HEIGHT = 420;
    private static final int CONNECTION_ERROR_HEIGHT = 460;

    private final AppController controller;
    private final JTextField username = new JTextField(20);
    private final JPasswordField password = new JPasswordField(20);
    private final JButton loginButton = Theme.primary("Đăng nhập");
    private final JButton retryButton = Theme.button("Thử kết nối lại");
    private final JLabel message = new JLabel(" ");

    public LoginFrame(AppController controller, String shopName) {
        super("Đăng nhập - Quản lý quán cà phê");
        this.controller = controller;
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setResizable(false);

        JPanel brand = new JPanel(new BorderLayout());
        brand.setBackground(Theme.DARK);
        brand.setPreferredSize(new Dimension(290, 0));
        brand.setBorder(Theme.pad(34, 28));
        JPanel brandText = new JPanel();
        brandText.setOpaque(false);
        brandText.setLayout(new BoxLayout(brandText, BoxLayout.Y_AXIS));
        JLabel name = new JLabel("<html><div style='width:220px'>" + shopName.replace("<", "&lt;") + "</div></html>");
        name.setFont(Theme.font(Font.BOLD, 28));
        name.setForeground(new Color(0xF3E6D6));
        JLabel tagline = new JLabel("<html>Hệ thống quản lý quán:<br>bán hàng, kho, báo cáo</html>");
        tagline.setForeground(new Color(0xCDB9A6));
        tagline.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));
        brandText.add(name);
        brandText.add(tagline);
        brand.add(brandText, BorderLayout.NORTH);
        JLabel version = new JLabel("Phiên bản 1.0");
        version.setForeground(new Color(0x9C8676));
        brand.add(version, BorderLayout.SOUTH);

        JPanel form = new JPanel();
        form.setBackground(Theme.SURFACE);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBorder(Theme.pad(40, 40));
        JLabel title = Theme.title("Đăng nhập");
        title.setFont(Theme.font(Font.BOLD, 20));
        form.add(left(title));
        form.add(Box.createVerticalStrut(4));
        form.add(left(Theme.muted("Dùng tài khoản do quản lý cấp")));
        form.add(Box.createVerticalStrut(22));
        form.add(left(new JLabel("Tên đăng nhập")));
        form.add(Box.createVerticalStrut(4));
        form.add(left(username));
        form.add(Box.createVerticalStrut(12));
        form.add(left(new JLabel("Mật khẩu")));
        form.add(Box.createVerticalStrut(4));
        form.add(left(password));
        form.add(Box.createVerticalStrut(10));
        message.setForeground(Theme.DANGER);
        form.add(left(message));
        form.add(Box.createVerticalStrut(10));
        loginButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        form.add(left(loginButton));
        form.add(Box.createVerticalStrut(8));
        retryButton.setVisible(false);
        form.add(left(retryButton));

        for (JTextField f : new JTextField[]{username, password}) {
            f.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
            f.setFont(Theme.font(Font.PLAIN, 14));
            Theme.input(f);
        }

        getContentPane().add(brand, BorderLayout.WEST);
        getContentPane().add(form, BorderLayout.CENTER);
        getRootPane().setDefaultButton(loginButton);
        loginButton.addActionListener(e -> submit());
        retryButton.addActionListener(e -> controller.connect());

        setSize(720, 420);
        setLocationRelativeTo(null);
    }

    private static Component left(javax.swing.JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        return c;
    }

    private void submit() {
        message.setText(" ");
        if (username.getText().isBlank() || password.getPassword().length == 0) {
            showError("Vui lòng nhập tên đăng nhập và mật khẩu.");
            return;
        }
        char[] pw = password.getPassword();
        password.setText("");
        controller.login(username.getText().trim(), pw);
    }

    public void setBusy(String text) {
        resizeHeight(NORMAL_HEIGHT);
        message.setForeground(Theme.MUTED);
        message.setText(text);
        loginButton.setEnabled(false);
        retryButton.setVisible(false);
    }

    public void setReady() {
        resizeHeight(NORMAL_HEIGHT);
        message.setForeground(Theme.DANGER);
        message.setText(" ");
        loginButton.setEnabled(true);
        retryButton.setVisible(false);
        (username.getText().isBlank() ? username : password).requestFocusInWindow();
    }

    public void setConnectionError(String text) {
        resizeHeight(CONNECTION_ERROR_HEIGHT);
        showError(text);
        loginButton.setEnabled(false);
        retryButton.setVisible(true);
    }

    private void resizeHeight(int height) {
        if (getHeight() != height) {
            setSize(getWidth(), height);
            setLocationRelativeTo(null);
        }
    }

    public void showError(String text) {
        message.setForeground(Theme.DANGER);
        message.setText("<html><div style='width:260px'>" + text.replace("<", "&lt;") + "</div></html>");
    }

    public void setUsername(String name) {
        username.setText(name);
    }
}
