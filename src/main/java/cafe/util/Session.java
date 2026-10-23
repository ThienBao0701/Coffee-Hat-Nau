package cafe.util;

import cafe.model.NhanVien;

import java.util.function.IntFunction;

/**
 * The authenticated user of this desktop application (one user at a time).
 * Every permission check re-reads the account from the database (through the loader installed by
 * Services), so locking, demoting or resetting an account takes effect immediately, even on a
 * terminal where that user is still logged in.
 */
public final class Session {

    private static volatile NhanVien current;
    private static volatile IntFunction<NhanVien> loader;

    private Session() {
    }

    /** Installed once by Services: loads an employee by id (null if missing). */
    public static void setLoader(IntFunction<NhanVien> accountLoader) {
        loader = accountLoader;
    }

    public static void login(NhanVien user) {
        current = user;
    }

    public static void logout() {
        current = null;
    }

    public static NhanVien current() {
        return current;
    }

    /** UI hint only (menus, buttons). Services always use require(). */
    public static boolean can(Permission p) {
        NhanVien u = current;
        return u != null && p.allows(u.vaiTro());
    }

    /** Returns the current user or throws if not logged in / not permitted. */
    public static NhanVien require(Permission p) {
        NhanVien u = requireLogin();
        if (!p.allows(u.vaiTro())) {
            throw new AccessDeniedException("Bạn không có quyền thực hiện thao tác này.");
        }
        return u;
    }

    /** Any logged-in, active user who has replaced their temporary password. */
    public static NhanVien requireLogin() {
        NhanVien u = refreshed();
        if (u.phaiDoiMatKhau()) {
            throw new AccessDeniedException("Bạn phải đổi mật khẩu tạm trước khi sử dụng hệ thống.");
        }
        return u;
    }

    /** Logged-in and active, temporary password allowed (used only to change the password). */
    public static NhanVien requireForPasswordChange() {
        return refreshed();
    }

    private static NhanVien refreshed() {
        NhanVien u = current;
        if (u == null) {
            throw new AccessDeniedException("Phiên làm việc đã kết thúc. Vui lòng đăng nhập lại.");
        }
        IntFunction<NhanVien> l = loader;
        if (l == null) {
            return u;
        }
        NhanVien fresh = l.apply(u.maNV());
        if (fresh == null || !fresh.trangThai()) {
            current = null;
            throw new AccessDeniedException("Tài khoản đã bị khóa hoặc không còn tồn tại. Vui lòng liên hệ quản lý.");
        }
        if (current != null) {
            current = fresh;
        }
        return fresh;
    }
}
