package cafe.service;

import cafe.config.Database;
import cafe.dao.NhanVienDao;
import cafe.model.NhanVien;
import cafe.model.Role;
import cafe.util.BusinessException;
import cafe.util.Check;
import cafe.util.Permission;
import cafe.util.Session;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Employee records and accounts (manager only). */
public class EmployeeService {

    private final Database db;
    private final AuditService audit;
    private final NhanVienDao dao = new NhanVienDao();

    public EmployeeService(Database db, AuditService audit) {
        this.db = db;
        this.audit = audit;
    }

    public List<NhanVien> findAll() {
        Session.require(Permission.MANAGE_EMPLOYEES);
        return db.read(dao::findAll);
    }

    /** Creates an employee with a temporary password; they must change it at first login. */
    public int create(NhanVien input, char[] tempPassword) {
        NhanVien me = Session.require(Permission.MANAGE_EMPLOYEES);
        NhanVien nv = normalize(input);
        if (me.vaiTro() != Role.CHU_CUA_HANG && nv.vaiTro() != Role.NHAN_VIEN) {
            throw new BusinessException("Quản lý chỉ được tạo tài khoản nhân viên.");
        }
        if (nv.vaiTro() == Role.CHU_CUA_HANG) {
            throw new BusinessException("Không thể tạo thêm tài khoản chủ cửa hàng từ màn hình nhân viên.");
        }
        Check.password(tempPassword);
        String hash = AuthService.hash(tempPassword);
        return db.tx(c -> {
            if (dao.usernameExists(c, nv.tenDangNhap(), null)) {
                throw new BusinessException("Tên đăng nhập \"" + nv.tenDangNhap() + "\" đã tồn tại.");
            }
            NhanVien toSave = new NhanVien(null, nv.tenNV(), nv.chucVu(), nv.soDienThoai(), nv.ngaySinh(), nv.diaChi(), nv.bangCap(),
                    nv.gioLam(), nv.heSoLuong(), nv.luongCoBan(), nv.tenDangNhap(), nv.vaiTro(), true, nv.trangThai());
            int id = dao.insert(c, toSave, hash);
            audit.log(c, "TAO_NHAN_VIEN", "NHAN_VIEN", id, nv.tenDangNhap() + " - vai trò " + nv.vaiTro());
            return id;
        });
    }

    public void update(NhanVien input) {
        NhanVien me = Session.require(Permission.MANAGE_EMPLOYEES);
        NhanVien nv = normalize(input);
        Check.notNull(nv.maNV(), "nhân viên");
        db.tx(c -> {
            NhanVien old = dao.findById(c, nv.maNV());
            if (old == null) {
                throw new BusinessException("Nhân viên không tồn tại.");
            }
            if (me.vaiTro() != Role.CHU_CUA_HANG
                    && (old.vaiTro() != Role.NHAN_VIEN || nv.vaiTro() != Role.NHAN_VIEN)) {
                throw new BusinessException("Quản lý chỉ được cập nhật tài khoản nhân viên.");
            }
            if (nv.vaiTro() == Role.CHU_CUA_HANG && old.vaiTro() != Role.CHU_CUA_HANG) {
                throw new BusinessException("Không thể gán vai trò chủ cửa hàng tại màn hình nhân viên.");
            }
            if (old.vaiTro() == Role.CHU_CUA_HANG && nv.vaiTro() != Role.CHU_CUA_HANG) {
                throw new BusinessException("Không thể hạ quyền chủ cửa hàng tại màn hình nhân viên.");
            }
            if (dao.usernameExists(c, nv.tenDangNhap(), nv.maNV())) {
                throw new BusinessException("Tên đăng nhập \"" + nv.tenDangNhap() + "\" đã tồn tại.");
            }
            if (nv.maNV().equals(me.maNV()) && (nv.vaiTro() != old.vaiTro() || !nv.trangThai())) {
                throw new BusinessException("Bạn không thể tự thay đổi vai trò hoặc khóa tài khoản của chính mình.");
            }
            boolean losesManager = old.vaiTro() == Role.QUAN_LY && old.trangThai() && (nv.vaiTro() != Role.QUAN_LY || !nv.trangThai());
            if (losesManager && dao.countActiveManagers(c) <= 1) {
                throw new BusinessException("Hệ thống phải còn ít nhất một tài khoản quản lý đang hoạt động.");
            }
            dao.update(c, nv);
            String detail = "Vai trò: " + old.vaiTro() + " -> " + nv.vaiTro() + "; Trạng thái: "
                    + (old.trangThai() ? "Hoạt động" : "Khóa") + " -> " + (nv.trangThai() ? "Hoạt động" : "Khóa");
            audit.log(c, "CAP_NHAT_NHAN_VIEN", "NHAN_VIEN", nv.maNV(), detail);
            return null;
        });
    }

    /** Manager sets a temporary password; the employee must change it at next login. */
    public void resetPassword(int maNV, char[] tempPassword) {
        NhanVien me = Session.require(Permission.MANAGE_EMPLOYEES);
        Check.password(tempPassword);
        String hash = AuthService.hash(tempPassword);
        db.tx(c -> {
            NhanVien target = dao.findById(c, maNV);
            if (target == null) {
                throw new BusinessException("Nhân viên không tồn tại.");
            }
            if (target.vaiTro() == Role.CHU_CUA_HANG && me.vaiTro() != Role.CHU_CUA_HANG) {
                throw new BusinessException("Chỉ chủ cửa hàng được đặt lại mật khẩu của chủ cửa hàng.");
            }
            if (target.vaiTro() == Role.QUAN_LY && me.vaiTro() != Role.CHU_CUA_HANG) {
                throw new BusinessException("Chỉ chủ cửa hàng được đặt lại mật khẩu của quản lý.");
            }
            dao.updatePassword(c, maNV, hash, true);
            audit.log(c, "DAT_LAI_MAT_KHAU", "NHAN_VIEN", maNV, null);
            return null;
        });
    }

    private static NhanVien normalize(NhanVien n) {
        LocalDate ngaySinh = n.ngaySinh();
        if (ngaySinh != null && ngaySinh.isAfter(LocalDate.now().minusYears(15))) {
            throw new BusinessException("Ngày sinh không hợp lệ (nhân viên phải từ 15 tuổi).");
        }
        BigDecimal heSo = Check.nonNegative(n.heSoLuong(), "Hệ số lương");
        BigDecimal luong = Check.nonNegative(n.luongCoBan(), "Lương cơ bản");
        return new NhanVien(n.maNV(), Check.required(n.tenNV(), "họ tên", 100), Check.optional(n.chucVu(), "Chức vụ", 50),
                Check.phone(n.soDienThoai(), false), ngaySinh, Check.optional(n.diaChi(), "Địa chỉ", 255),
                Check.optional(n.bangCap(), "Bằng cấp", 100), Check.optional(n.gioLam(), "Giờ làm", 50), heSo, luong,
                Check.username(n.tenDangNhap()), Check.notNull(n.vaiTro(), "vai trò"), n.phaiDoiMatKhau(), n.trangThai());
    }
}
