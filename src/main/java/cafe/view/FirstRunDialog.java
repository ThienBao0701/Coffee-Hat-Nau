package cafe.view;

import cafe.model.NhanVien;
import cafe.service.Services;
import cafe.view.common.Dialogs;
import cafe.view.common.Fields;
import cafe.view.common.FormDialog;
import cafe.view.common.Theme;

import javax.swing.JLabel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.WindowConstants;
import java.awt.Component;
import java.util.Arrays;

/**
 * One-time setup on an empty database: the person installing the app chooses the first owner's
 * username and password. There is no built-in default account.
 */
public final class FirstRunDialog {

    private FirstRunDialog() {
    }

    /** @return the created username, or null if the dialog was closed */
    public static String show(Component owner, Services services) {
        FormDialog f = new FormDialog(owner, "Thiết lập lần đầu - tạo tài khoản chủ cửa hàng");
        f.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        JLabel hint = Theme.muted("<html><div style='width:360px'>Cơ sở dữ liệu chưa có tài khoản quản lý. Hãy tạo tài khoản "
                + "chủ cửa hàng đầu tiên. Mật khẩu tối thiểu 8 ký tự, gồm cả chữ và số. Màn hình này chỉ xuất hiện một lần.</div></html>");
        JTextField name = Fields.text("");
        JTextField user = Fields.text("");
        JPasswordField pw = new JPasswordField(20);
        JPasswordField pw2 = new JPasswordField(20);
        f.addWide(hint);
        f.addRow("Họ tên *", name);
        f.addRow("Tên đăng nhập *", user);
        f.addRow("Mật khẩu *", pw);
        f.addRow("Nhập lại mật khẩu *", pw2);
        f.setSaveText("Tạo tài khoản");
        NhanVien[] created = new NhanVien[1];
        boolean ok = f.open(() -> {
            String n = name.getText();
            String u = user.getText();
            char[] p1 = pw.getPassword();
            char[] p2 = pw2.getPassword();
            return () -> {
                try {
                    created[0] = services.auth.createInitialOwner(n, u, p1, p2);
                } finally {
                    Arrays.fill(p1, '\0');
                    Arrays.fill(p2, '\0');
                }
            };
        });
        if (!ok) {
            return null;
        }
        Dialogs.info(owner, "Đã tạo tài khoản chủ cửa hàng \"" + created[0].tenDangNhap() + "\". Vui lòng đăng nhập.");
        return created[0].tenDangNhap();
    }
}
