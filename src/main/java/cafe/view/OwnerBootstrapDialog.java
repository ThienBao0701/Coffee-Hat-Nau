package cafe.view;

import cafe.service.Services;
import cafe.view.common.FormDialog;
import cafe.view.common.Theme;

import javax.swing.JPasswordField;
import javax.swing.JTextField;
import java.awt.Component;
import java.util.Arrays;

/** Explicit recovery path for an existing manager account; enabled only with cafe.owner.bootstrap=true. */
public final class OwnerBootstrapDialog {
    private OwnerBootstrapDialog() {
    }

    public static String show(Component parent, Services services) {
        FormDialog f = new FormDialog(parent, "Khôi phục chủ cửa hàng");
        f.addWide(Theme.muted("<html><div style='width:360px'>Cơ sở dữ liệu hiện có chưa có chủ cửa hàng. "
                + "Nhập tài khoản quản lý và mật khẩu hiện tại để chuyển chính tài khoản đó thành chủ cửa hàng. "
                + "Quy trình chỉ chạy một lần.</div></html>"));
        JTextField user = new JTextField(20);
        JPasswordField password = new JPasswordField(20);
        f.addRow("Tên đăng nhập quản lý", user);
        f.addRow("Mật khẩu hiện tại", password);
        f.setSaveText("Xác nhận khôi phục");
        String[] promoted = new String[1];
        boolean ok = f.open(() -> {
            String username = user.getText().trim();
            char[] pw = password.getPassword();
            return () -> {
                try {
                    promoted[0] = services.auth.promoteExistingManagerToOwner(username, pw).tenDangNhap();
                } finally {
                    Arrays.fill(pw, '\0');
                    password.setText("");
                }
            };
        });
        return ok ? promoted[0] : null;
    }
}
