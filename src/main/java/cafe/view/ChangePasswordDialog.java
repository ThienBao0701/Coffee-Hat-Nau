package cafe.view;

import cafe.service.Services;
import cafe.view.common.FormDialog;
import cafe.view.common.Theme;

import javax.swing.JPasswordField;
import java.awt.Component;
import java.util.Arrays;

/** Change own password. Forced after a manager creates or resets the account. */
public final class ChangePasswordDialog {

    private ChangePasswordDialog() {
    }

    public static boolean show(Component owner, Services services, boolean forced) {
        FormDialog f = new FormDialog(owner, "Đổi mật khẩu");
        if (forced) {
            f.addWide(Theme.muted("<html><div style='width:340px'>Bạn đang dùng mật khẩu tạm thời. Vui lòng đặt mật khẩu mới "
                    + "(tối thiểu 8 ký tự, gồm chữ và số) để tiếp tục.</div></html>"));
        }
        JPasswordField old = new JPasswordField(20);
        JPasswordField pw = new JPasswordField(20);
        JPasswordField pw2 = new JPasswordField(20);
        f.addRow(forced ? "Mật khẩu tạm *" : "Mật khẩu hiện tại *", old);
        f.addRow("Mật khẩu mới *", pw);
        f.addRow("Nhập lại mật khẩu mới *", pw2);
        f.setSaveText("Đổi mật khẩu");
        return f.open(() -> {
            char[] a = old.getPassword();
            char[] b = pw.getPassword();
            char[] c = pw2.getPassword();
            return () -> {
                try {
                    services.auth.changePassword(a, b, c);
                } finally {
                    Arrays.fill(a, '\0');
                    Arrays.fill(b, '\0');
                    Arrays.fill(c, '\0');
                }
            };
        });
    }
}
