package cafe.service;

import cafe.config.Database;
import cafe.dao.NhanVienDao;
import cafe.model.NhanVien;
import cafe.model.Role;
import cafe.util.BusinessException;
import cafe.util.Check;
import cafe.util.Session;
import org.mindrot.jbcrypt.BCrypt;

import java.math.BigDecimal;
import java.util.Arrays;

/** Login, logout, password change and the one-time initial owner setup. */
public class AuthService {

    static final int BCRYPT_COST = 10;
    private static final String LOGIN_FAILED = "Tên đăng nhập hoặc mật khẩu không đúng, hoặc tài khoản đã bị khóa.";

    private final Database db;
    private final AuditService audit;
    private final boolean ownerBootstrapEnabled;
    private final NhanVienDao dao = new NhanVienDao();
    // Checked when the username does not exist, so a wrong username takes as long as a wrong password.
    private final String dummyHash = BCrypt.hashpw("not-a-real-password", BCrypt.gensalt(BCRYPT_COST));

    public AuthService(Database db, AuditService audit, boolean ownerBootstrapEnabled) {
        this.db = db;
        this.audit = audit;
        this.ownerBootstrapEnabled = ownerBootstrapEnabled;
    }

    static String hash(char[] password) {
        return BCrypt.hashpw(new String(password), BCrypt.gensalt(BCRYPT_COST));
    }

    static boolean matches(char[] password, String hash) {
        return hash != null && BCrypt.checkpw(new String(password), hash);
    }

    public NhanVien login(String username, char[] password) {
        if (username == null || username.isBlank() || password == null || password.length == 0) {
            throw new BusinessException("Vui lòng nhập tên đăng nhập và mật khẩu.");
        }
        String user = username.trim();
        NhanVienDao.Credentials creds = db.read(c -> dao.findCredentials(c, user));
        boolean ok = matches(password, creds == null ? dummyHash : creds.passwordHash()) && creds != null && creds.nhanVien().trangThai();
        if (!ok) {
            db.tx(c -> {
                audit.log(c, "DANG_NHAP_THAT_BAI", "NHAN_VIEN", null, "Tên đăng nhập: " + user);
                return null;
            });
            throw new BusinessException(LOGIN_FAILED);
        }
        Session.login(creds.nhanVien());
        db.tx(c -> {
            audit.log(c, "DANG_NHAP", "NHAN_VIEN", creds.nhanVien().maNV(), null);
            return null;
        });
        return creds.nhanVien();
    }

    public void logout() {
        if (Session.current() != null) {
            db.tx(c -> {
                audit.log(c, "DANG_XUAT", "NHAN_VIEN", Session.current().maNV(), null);
                return null;
            });
        }
        Session.logout();
    }

    /** True when a fresh database has no active manager/owner account yet. */
    public boolean needsInitialSetup() {
        return db.read(dao::countActiveManagers) == 0;
    }

    /**
     * Creates the first manager account with a password chosen by the person installing the app.
     * Refused as soon as any active manager exists, so it cannot be used to gain privileges later.
     */
    public NhanVien createInitialOwner(String tenNV, String username, char[] password, char[] confirm) {
        String ten = Check.required(tenNV, "họ tên", 100);
        String user = Check.username(username);
        Check.password(password);
        if (!Arrays.equals(password, confirm)) {
            throw new BusinessException("Mật khẩu nhập lại không khớp.");
        }
        String hash = hash(password);
        return db.tx(c -> {
            if (dao.countActiveManagers(c) > 0) {
                throw new BusinessException("Hệ thống đã có tài khoản quản lý. Vui lòng đăng nhập.");
            }
            if (dao.usernameExists(c, user, null)) {
                throw new BusinessException("Tên đăng nhập đã tồn tại.");
            }
            NhanVien nv = new NhanVien(null, ten, "Chủ cửa hàng", null, null, null, null, null, BigDecimal.ONE, BigDecimal.ZERO,
                    user, Role.CHU_CUA_HANG, false, true);
            int id = dao.insert(c, nv, hash);
            audit.log(c, "THIET_LAP_BAN_DAU", "NHAN_VIEN", id, "Tạo tài khoản chủ cửa hàng đầu tiên: " + user);
            return dao.findById(c, id);
        });
    }

    /** Compatibility entry point retained for existing callers; first-run setup now creates the owner. */
    @Deprecated
    public NhanVien createInitialManager(String tenNV, String username, char[] password, char[] confirm) {
        return createInitialOwner(tenNV, username, password, confirm);
    }

    public boolean needsOwnerBootstrap() {
        return ownerBootstrapEnabled && db.read(dao::countActiveOwners) == 0 && !needsInitialSetup();
    }

    /** Explicit local recovery for a legacy database: requires the opt-in JVM flag and a manager credential. */
    public NhanVien promoteExistingManagerToOwner(String username, char[] password) {
        if (!ownerBootstrapEnabled) {
            throw new BusinessException("Khởi tạo chủ cửa hàng hiện chưa được bật.");
        }
        if (username == null || username.isBlank() || password == null || password.length == 0) {
            throw new BusinessException("Nhập tài khoản quản lý và mật khẩu hiện tại.");
        }
        String user = username.trim();
        return db.tx(c -> {
            if (dao.countActiveOwners(c) > 0) {
                throw new BusinessException("Đã có tài khoản chủ cửa hàng; không thể chạy khôi phục lần nữa.");
            }
            NhanVienDao.Credentials creds = dao.findCredentials(c, user);
            if (creds == null || !creds.nhanVien().trangThai() || creds.nhanVien().vaiTro() != Role.QUAN_LY
                    || !matches(password, creds.passwordHash())) {
                throw new BusinessException("Thông tin quản lý không hợp lệ.");
            }
            dao.promoteOwner(c, creds.nhanVien().maNV());
            audit.log(c, "KHOI_PHUC_CHU_CUA_HANG", "NHAN_VIEN", creds.nhanVien().maNV(),
                    "Khôi phục owner theo cờ khởi động cục bộ");
            return dao.findById(c, creds.nhanVien().maNV());
        });
    }

    public void changePassword(char[] oldPassword, char[] newPassword, char[] confirm) {
        NhanVien me = Session.requireForPasswordChange();
        Check.password(newPassword);
        if (!Arrays.equals(newPassword, confirm)) {
            throw new BusinessException("Mật khẩu nhập lại không khớp.");
        }
        if (Arrays.equals(oldPassword, newPassword)) {
            throw new BusinessException("Mật khẩu mới phải khác mật khẩu hiện tại.");
        }
        String newHash = hash(newPassword);
        NhanVien updated = db.tx(c -> {
            if (!matches(oldPassword, dao.findPasswordHash(c, me.maNV()))) {
                throw new BusinessException("Mật khẩu hiện tại không đúng.");
            }
            dao.updatePassword(c, me.maNV(), newHash, false);
            audit.log(c, "DOI_MAT_KHAU", "NHAN_VIEN", me.maNV(), null);
            return dao.findById(c, me.maNV());
        });
        Session.login(updated);
    }
}
