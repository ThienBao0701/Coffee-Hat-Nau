package cafe.view.admin;

import cafe.model.NhanVien;
import cafe.model.Role;
import cafe.service.Services;
import cafe.util.BusinessException;
import cafe.util.Session;
import cafe.view.common.Column;
import cafe.view.common.DataTable;
import cafe.view.common.Dialogs;
import cafe.view.common.Fields;
import cafe.view.common.FormDialog;
import cafe.view.common.ListPanel;
import cafe.view.common.Refreshable;
import cafe.view.common.Theme;
import cafe.view.common.UiTask;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/** Employees and their accounts: role, active status, temporary passwords (manager only). */
public class EmployeePanel extends JPanel implements Refreshable {

    private final Services services;
    private final ListPanel<NhanVien> list;

    public EmployeePanel(Services services) {
        super(new BorderLayout());
        this.services = services;
        list = new ListPanel<>(services.employees::findAll, new DataTable<>(
                Column.integer("Mã", 50, NhanVien::maNV),
                Column.text("Họ tên", 170, NhanVien::tenNV),
                Column.text("Tên đăng nhập", 120, NhanVien::tenDangNhap),
                Column.text("Vai trò", 90, NhanVien::vaiTro),
                Column.text("Chức vụ", 100, n -> n.chucVu() == null ? "" : n.chucVu()),
                Column.text("Điện thoại", 100, n -> n.soDienThoai() == null ? "" : n.soDienThoai()),
                Column.text("Giờ làm", 90, n -> n.gioLam() == null ? "" : n.gioLam()),
                Column.money("Lương tháng", 110, NhanVien::tinhLuong),
                Column.text("Tài khoản", 100, n -> n.trangThai() ? "Hoạt động" : "Đã khóa")));
        list.table.setRowColor(n -> n.trangThai() ? null : new java.awt.Color(0xF1ECE7));
        list.action(Theme.primary("Thêm nhân viên"), false, n -> edit(null));
        list.action(Theme.button("Sửa"), true, this::edit);
        list.action(Theme.button("Khóa / mở khóa"), true, this::toggleActive);
        list.action(Theme.button("Đặt lại mật khẩu"), true, this::resetPassword);
        list.table.onDoubleClick(this::edit);
        add(Theme.screen("Nhân viên & tài khoản", list), BorderLayout.CENTER);
    }

    @Override
    public void reload() {
        list.reload();
    }

    private void edit(NhanVien n) {
        FormDialog f = new FormDialog(this, n == null ? "Thêm nhân viên" : "Sửa nhân viên");
        JTextField ten = Fields.text(n == null ? "" : n.tenNV());
        JTextField user = Fields.text(n == null ? "" : n.tenDangNhap());
        if (n != null && Session.current().vaiTro() != Role.CHU_CUA_HANG
                && n.vaiTro() != Role.NHAN_VIEN) {
            Dialogs.error(this, new BusinessException("Chỉ chủ cửa hàng được chỉnh sửa tài khoản quản lý."));
            return;
        }
        List<Role> roles = Session.current().vaiTro() == Role.CHU_CUA_HANG
                ? (n != null && n.vaiTro() == Role.CHU_CUA_HANG
                    ? List.of(Role.CHU_CUA_HANG)
                    : List.of(Role.NHAN_VIEN, Role.QUAN_LY))
                : List.of(Role.NHAN_VIEN);
        JComboBox<Role> role = Fields.combo(roles, n == null ? Role.NHAN_VIEN : n.vaiTro());
        if (n != null && n.maNV().equals(Session.current().maNV())) {
            role.setEnabled(false);
        }
        JTextField chucVu = Fields.text(n == null ? "" : n.chucVu());
        JTextField sdt = Fields.text(n == null ? "" : n.soDienThoai());
        JTextField ngaySinh = Fields.date(n == null ? null : n.ngaySinh());
        JTextField diaChi = Fields.text(n == null ? "" : n.diaChi());
        JTextField bangCap = Fields.text(n == null ? "" : n.bangCap());
        JTextField gioLam = Fields.text(n == null ? "" : n.gioLam());
        JTextField heSo = Fields.decimal(n == null ? BigDecimal.ONE : n.heSoLuong());
        JTextField luong = Fields.money(n == null ? BigDecimal.ZERO : n.luongCoBan());
        JCheckBox active = Fields.check("Tài khoản hoạt động", n == null || n.trangThai());
        JPasswordField pw = new JPasswordField(20);
        JPasswordField pw2 = new JPasswordField(20);
        f.addRow("Họ tên *", ten).addRow("Tên đăng nhập *", user).addRow("Vai trò *", role).addRow("Chức vụ", chucVu)
                .addRow("Điện thoại", sdt).addRow("Ngày sinh (dd/MM/yyyy)", ngaySinh).addRow("Địa chỉ", diaChi)
                .addRow("Bằng cấp", bangCap).addRow("Giờ làm / ca", gioLam).addRow("Hệ số lương *", heSo)
                .addRow("Lương cơ bản (VNĐ) *", luong);
        if (n == null) {
            f.addRow("Mật khẩu tạm *", pw).addRow("Nhập lại mật khẩu *", pw2);
            f.addWide(Theme.muted("Nhân viên phải đổi mật khẩu tạm ở lần đăng nhập đầu tiên."));
        }
        f.addRow("", active);
        if (f.open(() -> {
            NhanVien input = new NhanVien(n == null ? null : n.maNV(), ten.getText(), chucVu.getText(), sdt.getText(),
                    Fields.getDate(ngaySinh, "Ngày sinh"), diaChi.getText(), bangCap.getText(), gioLam.getText(),
                    Fields.getDecimal(heSo, "Hệ số lương"), Fields.getMoney(luong, "Lương cơ bản"), user.getText(),
                    (Role) role.getSelectedItem(), n != null && n.phaiDoiMatKhau(), active.isSelected());
            if (n != null) {
                return () -> services.employees.update(input);
            }
            char[] p1 = pw.getPassword();
            char[] p2 = pw2.getPassword();
            if (!Arrays.equals(p1, p2)) {
                throw new BusinessException("Mật khẩu nhập lại không khớp.");
            }
            return () -> {
                try {
                    services.employees.create(input, p1);
                } finally {
                    Arrays.fill(p1, '\0');
                    Arrays.fill(p2, '\0');
                }
            };
        })) {
            list.reload();
        }
    }

    private void toggleActive(NhanVien n) {
        String q = n.trangThai() ? "Khóa tài khoản của " + n.tenNV() + "? Nhân viên sẽ không thể đăng nhập."
                : "Mở khóa tài khoản của " + n.tenNV() + "?";
        if (!Dialogs.confirm(this, q)) {
            return;
        }
        NhanVien changed = new NhanVien(n.maNV(), n.tenNV(), n.chucVu(), n.soDienThoai(), n.ngaySinh(), n.diaChi(), n.bangCap(),
                n.gioLam(), n.heSoLuong(), n.luongCoBan(), n.tenDangNhap(), n.vaiTro(), n.phaiDoiMatKhau(), !n.trangThai());
        UiTask.exec(this, () -> services.employees.update(changed), list::reload);
    }

    private void resetPassword(NhanVien n) {
        FormDialog f = new FormDialog(this, "Đặt lại mật khẩu - " + n.tenNV());
        JPasswordField pw = new JPasswordField(20);
        JPasswordField pw2 = new JPasswordField(20);
        f.addRow("Mật khẩu tạm mới *", pw).addRow("Nhập lại *", pw2);
        f.addWide(Theme.muted("Nhân viên sẽ phải đổi mật khẩu ở lần đăng nhập tiếp theo."));
        f.setSaveText("Đặt lại");
        if (f.open(() -> {
            char[] p1 = pw.getPassword();
            char[] p2 = pw2.getPassword();
            if (!Arrays.equals(p1, p2)) {
                throw new BusinessException("Mật khẩu nhập lại không khớp.");
            }
            return () -> {
                try {
                    services.employees.resetPassword(n.maNV(), p1);
                } finally {
                    Arrays.fill(p1, '\0');
                    Arrays.fill(p2, '\0');
                }
            };
        })) {
            Dialogs.info(this, "Đã đặt lại mật khẩu cho " + n.tenNV() + ".");
            list.reload();
        }
    }
}
