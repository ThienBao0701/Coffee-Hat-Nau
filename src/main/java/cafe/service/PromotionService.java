package cafe.service;

import cafe.config.Database;
import cafe.dao.PromotionDao;
import cafe.model.KhuyenMai;
import cafe.model.LoaiKhuyenMai;
import cafe.model.Thue;
import cafe.util.BusinessException;
import cafe.util.Check;
import cafe.util.Dates;
import cafe.util.Money;
import cafe.util.Permission;
import cafe.util.Session;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Promotion types, promotions and taxes. Rates are data, never hard-coded. */
public class PromotionService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final Database db;
    private final AuditService audit;
    private final PromotionDao dao = new PromotionDao();

    public PromotionService(Database db, AuditService audit) {
        this.db = db;
        this.audit = audit;
    }

    // ---------- promotion types ----------
    public List<LoaiKhuyenMai> findLoai() {
        Session.require(Permission.MANAGE_PROMOTIONS);
        return db.read(dao::findLoai);
    }

    public void saveLoai(LoaiKhuyenMai input) {
        Session.require(Permission.MANAGE_PROMOTIONS);
        String ten = Check.required(input.tenLoaiKM(), "tên loại khuyến mãi", 100);
        BigDecimal pct = input.phanTramGiam();
        BigDecimal amount = input.phanTienGiam();
        if ((pct == null) == (amount == null)) {
            throw new BusinessException("Loại khuyến mãi phải là giảm theo % HOẶC giảm số tiền cố định.");
        }
        if (pct != null && (pct.signum() <= 0 || pct.compareTo(HUNDRED) > 0)) {
            throw new BusinessException("Phần trăm giảm phải lớn hơn 0 và không quá 100.");
        }
        if (amount != null) {
            Check.positive(amount, "Số tiền giảm");
            amount = Money.round(amount);
        }
        LoaiKhuyenMai l = new LoaiKhuyenMai(input.maLoaiKM(), ten, pct, amount);
        db.tx(c -> {
            if (l.maLoaiKM() == null) {
                audit.log(c, "THEM", "LOAI_KHUYEN_MAI", dao.insertLoai(c, l), ten + " " + l.moTaGiam());
            } else {
                dao.updateLoai(c, l);
                audit.log(c, "SUA", "LOAI_KHUYEN_MAI", l.maLoaiKM(), ten + " " + l.moTaGiam());
            }
            return null;
        });
    }

    public void deleteLoai(int id) {
        Session.require(Permission.MANAGE_PROMOTIONS);
        db.tx(c -> {
            if (dao.countKhuyenMaiOfLoai(c, id) > 0) {
                throw new BusinessException("Loại khuyến mãi đang được dùng, không thể xóa.");
            }
            dao.deleteLoai(c, id);
            audit.log(c, "XOA", "LOAI_KHUYEN_MAI", id, null);
            return null;
        });
    }

    // ---------- promotions ----------
    public List<KhuyenMai> findKhuyenMai() {
        Session.require(Permission.MANAGE_PROMOTIONS);
        return db.read(dao::findKhuyenMai);
    }

    public void saveKhuyenMai(KhuyenMai input) {
        Session.require(Permission.MANAGE_PROMOTIONS);
        String ten = Check.required(input.tenKM(), "tên khuyến mãi", 100);
        LocalDate start = Check.notNull(input.ngayBatDau(), "ngày bắt đầu");
        LocalDate end = Check.notNull(input.ngayKetThuc(), "ngày kết thúc");
        Check.dateRange(start, end, "khuyến mãi");
        BigDecimal min = input.giaTriDonToiThieu() == null ? BigDecimal.ZERO : Check.nonNegative(input.giaTriDonToiThieu(), "Giá trị đơn tối thiểu");
        BigDecimal max = input.giamToiDa() == null ? null : Check.positive(input.giamToiDa(), "Giảm tối đa");
        KhuyenMai k = new KhuyenMai(input.maKM(), ten, input.maLoaiKM(), null, null, null, start, end, min, max, input.trangThai());
        db.tx(c -> {
            if (k.maKM() == null) {
                audit.log(c, "THEM", "KHUYEN_MAI", dao.insertKhuyenMai(c, k), ten);
            } else {
                dao.updateKhuyenMai(c, k);
                audit.log(c, "SUA", "KHUYEN_MAI", k.maKM(), ten);
            }
            return null;
        });
    }

    /** Promotions usable today for an order of this subtotal. */
    public List<KhuyenMai> findEligible(BigDecimal tamTinh) {
        Session.require(Permission.SELL);
        LocalDate today = LocalDate.now();
        return db.read(c -> dao.findKhuyenMaiActiveOn(c, today)).stream()
                .filter(k -> tamTinh.compareTo(k.giaTriDonToiThieu()) >= 0)
                .toList();
    }

    // ---------- taxes ----------
    public List<Thue> findThue() {
        Session.requireLogin();
        return db.read(dao::findThue);
    }

    public Thue findThueToday() {
        Session.requireLogin();
        return db.read(c -> dao.findThueOn(c, LocalDate.now()));
    }

    /** Tax periods may not overlap, so exactly one rate (or none) applies on any date. */
    public void saveThue(Thue input) {
        Session.require(Permission.MANAGE_TAXES);
        String ten = Check.required(input.tenThue(), "tên thuế", 100);
        BigDecimal pct = Check.nonNegative(input.phanTram(), "Thuế suất");
        if (pct.compareTo(HUNDRED) > 0) {
            throw new BusinessException("Thuế suất không được quá 100%.");
        }
        LocalDate start = Check.notNull(input.ngayBatDau(), "ngày bắt đầu");
        Check.dateRange(start, input.ngayKetThuc(), "thuế");
        Thue t = new Thue(input.maThue(), ten, pct, start, input.ngayKetThuc());
        db.tx(c -> {
            List<Thue> overlaps = dao.findThueOverlapping(c, start, t.ngayKetThuc(), t.maThue());
            if (!overlaps.isEmpty()) {
                Thue o = overlaps.get(0);
                throw new BusinessException("Thời gian áp dụng trùng với \"" + o.tenThue() + "\" (từ " + Dates.format(o.ngayBatDau())
                        + "). Hãy đặt ngày kết thúc cho mức thuế cũ trước.");
            }
            if (t.maThue() == null) {
                audit.log(c, "THEM", "THUE", dao.insertThue(c, t), ten + " " + pct + "%");
            } else {
                dao.updateThue(c, t);
                audit.log(c, "SUA", "THUE", t.maThue(), ten + " " + pct + "%");
            }
            return null;
        });
    }
}
