package cafe.service;

import cafe.config.Database;
import cafe.dao.CustomerDao;
import cafe.dao.TableDao;
import cafe.model.Ban;
import cafe.model.DonDatHang;
import cafe.model.KhachHang;
import cafe.model.NhanVien;
import cafe.model.TrangThaiDatBan;
import cafe.util.BusinessException;
import cafe.util.Check;
import cafe.util.Dates;
import cafe.util.Permission;
import cafe.util.Session;
import cafe.util.Text;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Customers and table reservations. */
public class CustomerService {

    private final Database db;
    private final AuditService audit;
    private final int reservationMinutes;
    private final CustomerDao dao = new CustomerDao();
    private final TableDao tableDao = new TableDao();

    public CustomerService(Database db, AuditService audit, int reservationMinutes) {
        this.db = db;
        this.audit = audit;
        this.reservationMinutes = reservationMinutes;
    }

    /** ID-card numbers are personal data: only managers see them in full. */
    private static KhachHang protect(KhachHang k) {
        if (k == null || Session.can(Permission.VIEW_CUSTOMER_ID)) {
            return k;
        }
        return new KhachHang(k.maKH(), k.tenKH(), k.ngaySinh(), k.soCMND() == null ? null : Text.mask(k.soCMND()), k.soDienThoai(), k.diaChi());
    }

    public List<KhachHang> search(String keyword) {
        Session.require(Permission.MANAGE_CUSTOMERS);
        return db.read(c -> dao.search(c, keyword)).stream().map(CustomerService::protect).toList();
    }

    public KhachHang findByPhone(String phone) {
        Session.require(Permission.MANAGE_CUSTOMERS);
        String p = Check.phone(phone, true);
        return protect(db.read(c -> dao.findByPhone(c, p)));
    }

    public int save(KhachHang input) {
        Session.require(Permission.MANAGE_CUSTOMERS);
        KhachHang raw = input;
        if (raw.maKH() != null && !Session.can(Permission.VIEW_CUSTOMER_ID)) {
            // the form shows a masked ID number; drop it here, saveInTx keeps the stored value
            raw = new KhachHang(raw.maKH(), raw.tenKH(), raw.ngaySinh(), null, raw.soDienThoai(), raw.diaChi());
        }
        KhachHang k = normalize(raw);
        return db.tx(c -> saveInTx(c, k));
    }

    private int saveInTx(java.sql.Connection c, KhachHang input) throws java.sql.SQLException {
        KhachHang k = input;
        if (k.maKH() != null && !Session.can(Permission.VIEW_CUSTOMER_ID)) {
            // staff only see a masked ID number, so they must not overwrite the real one
            KhachHang old = dao.findById(c, k.maKH());
            k = new KhachHang(k.maKH(), k.tenKH(), k.ngaySinh(), old == null ? null : old.soCMND(), k.soDienThoai(), k.diaChi());
        }
        if (k.soDienThoai() != null && dao.phoneExists(c, k.soDienThoai(), k.maKH())) {
            throw new BusinessException("Số điện thoại " + k.soDienThoai() + " đã thuộc về khách hàng khác.");
        }
        if (k.soCMND() != null && dao.cmndExists(c, k.soCMND(), k.maKH())) {
            throw new BusinessException("Số CMND/CCCD đã thuộc về khách hàng khác.");
        }
        if (k.maKH() == null) {
            int id = dao.insert(c, k);
            audit.log(c, "THEM", "KHACH_HANG", id, k.tenKH());
            return id;
        }
        dao.update(c, k);
        audit.log(c, "SUA", "KHACH_HANG", k.maKH(), k.tenKH());
        return k.maKH();
    }

    public void delete(int maKH) {
        Session.require(Permission.DELETE_CUSTOMER);
        db.tx(c -> {
            if (dao.countReferences(c, maKH) > 0) {
                throw new BusinessException("Khách hàng đã có hóa đơn hoặc đặt bàn nên không thể xóa (cần giữ lịch sử).");
            }
            dao.delete(c, maKH);
            audit.log(c, "XOA", "KHACH_HANG", maKH, null);
            return null;
        });
    }

    private static KhachHang normalize(KhachHang k) {
        if (k.ngaySinh() != null && k.ngaySinh().isAfter(LocalDate.now())) {
            throw new BusinessException("Ngày sinh không được ở tương lai.");
        }
        return new KhachHang(k.maKH(), Check.required(k.tenKH(), "tên khách hàng", 100), k.ngaySinh(),
                Check.cmnd(k.soCMND()), Check.phone(k.soDienThoai(), false), Check.optional(k.diaChi(), "Địa chỉ", 255));
    }

    // ---------- reservations ----------
    public List<DonDatHang> findReservations(LocalDate from, LocalDate to, TrangThaiDatBan status) {
        Session.require(Permission.MANAGE_RESERVATIONS);
        Check.dateRange(from, to, "khoảng thời gian");
        return db.read(c -> dao.findReservations(c, from.atStartOfDay(), to.plusDays(1).atStartOfDay(), status));
    }

    /**
     * Creates a pending reservation. The customer is either an existing one (maKH) or a new one
     * (tenKH + phone, reused if the phone is already known). The table must be free of other
     * active reservations within +/- reservation.durationMinutes.
     */
    public int createReservation(Integer maKH, String tenKHMoi, String sdtMoi, int maBan, LocalDateTime thoiDiem, int soNguoi, String ghiChu) {
        NhanVien me = Session.require(Permission.MANAGE_RESERVATIONS);
        Check.notNull(thoiDiem, "thời điểm đặt");
        if (!thoiDiem.isAfter(LocalDateTime.now())) {
            throw new BusinessException("Thời điểm đặt bàn phải ở tương lai.");
        }
        if (thoiDiem.isAfter(LocalDateTime.now().plusDays(90))) {
            throw new BusinessException("Chỉ nhận đặt bàn trước tối đa 90 ngày.");
        }
        Check.positive(soNguoi, "Số người");
        String note = Check.optional(ghiChu, "Ghi chú", 255);
        KhachHang newCustomer = maKH == null
                ? normalize(new KhachHang(null, tenKHMoi, null, null, Check.phone(sdtMoi, true), null)) : null;
        return db.tx(c -> {
            tableDao.lockBan(c, maBan);
            Ban ban = tableDao.findBanById(c, maBan);
            if (ban == null || !ban.trangThai()) {
                throw new BusinessException("Bàn không tồn tại hoặc đang ngừng phục vụ.");
            }
            if (soNguoi > ban.soCho()) {
                throw new BusinessException(ban.tenBan() + " chỉ có " + ban.soCho() + " chỗ.");
            }
            List<DonDatHang> conflicts = dao.findActiveReservations(c, maBan, thoiDiem.minusMinutes(reservationMinutes),
                    thoiDiem.plusMinutes(reservationMinutes), null);
            if (!conflicts.isEmpty()) {
                DonDatHang x = conflicts.get(0);
                throw new BusinessException(ban.tenBan() + " đã được đặt lúc " + Dates.format(x.thoiDiemDat())
                        + " (khách " + x.tenKH() + "). Vui lòng chọn bàn hoặc giờ khác.");
            }
            int customerId;
            if (maKH != null) {
                if (dao.findById(c, maKH) == null) {
                    throw new BusinessException("Khách hàng không tồn tại.");
                }
                customerId = maKH;
            } else {
                KhachHang existing = dao.findByPhone(c, newCustomer.soDienThoai());
                customerId = existing != null ? existing.maKH() : saveInTx(c, newCustomer);
            }
            int id = dao.insertReservation(c, new DonDatHang(null, customerId, null, null, maBan, null, me.maNV(), null,
                    thoiDiem, soNguoi, note, TrangThaiDatBan.CHO_XAC_NHAN, LocalDateTime.now()));
            audit.log(c, "DAT_BAN", "DON_DAT_HANG", id, ban.tenBan() + " lúc " + Dates.format(thoiDiem));
            return id;
        });
    }

    public void confirmReservation(int maDDH) {
        changeStatus(maDDH, TrangThaiDatBan.DA_XAC_NHAN, "XAC_NHAN_DAT_BAN", TrangThaiDatBan.CHO_XAC_NHAN);
    }

    public void cancelReservation(int maDDH) {
        changeStatus(maDDH, TrangThaiDatBan.DA_HUY, "HUY_DAT_BAN", TrangThaiDatBan.CHO_XAC_NHAN, TrangThaiDatBan.DA_XAC_NHAN);
    }

    private void changeStatus(int maDDH, TrangThaiDatBan to, String action, TrangThaiDatBan... from) {
        Session.require(Permission.MANAGE_RESERVATIONS);
        db.tx(c -> {
            if (dao.updateReservationStatus(c, maDDH, to, from) != 1) {
                throw new BusinessException("Không thể chuyển đơn đặt bàn sang trạng thái \"" + to + "\" từ trạng thái hiện tại.");
            }
            audit.log(c, action, "DON_DAT_HANG", maDDH, null);
            return null;
        });
    }
}
