package cafe.service;

import cafe.config.Database;
import cafe.dao.CatalogDao;
import cafe.dao.PriceDao;
import cafe.model.BangGia;
import cafe.model.ChiTietBangGia;
import cafe.model.LoaiMon;
import cafe.model.MenuItem;
import cafe.model.Mon;
import cafe.model.Size;
import cafe.model.Topping;
import cafe.util.BusinessException;
import cafe.util.Check;
import cafe.util.Dates;
import cafe.util.Money;
import cafe.util.Permission;
import cafe.util.Session;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Products, categories, sizes, toppings and price lists.
 * Price list rules: periods never overlap, so each product/size has exactly one current price;
 * lists that have ended are read-only history; only lists that have not started yet can be deleted.
 */
public class CatalogService {

    /** One price to save into a price list. */
    public record PriceEntry(int maMon, int maSize, BigDecimal giaBan) {
    }

    private final Database db;
    private final AuditService audit;
    private final CatalogDao dao = new CatalogDao();
    private final PriceDao priceDao = new PriceDao();

    public CatalogService(Database db, AuditService audit) {
        this.db = db;
        this.audit = audit;
    }

    // ---------- categories ----------
    public List<LoaiMon> findLoaiMon() {
        Session.requireLogin();
        return db.read(dao::findLoaiMon);
    }

    public void saveLoaiMon(LoaiMon input) {
        Session.require(Permission.MANAGE_CATALOG);
        String ten = Check.required(input.tenLoai(), "tên loại món", 100);
        db.tx(c -> {
            if (dao.loaiMonNameExists(c, ten, input.maLoai())) {
                throw new BusinessException("Loại món \"" + ten + "\" đã tồn tại.");
            }
            if (input.maLoai() == null) {
                audit.log(c, "THEM", "LOAI_MON", dao.insertLoaiMon(c, ten), ten);
            } else {
                dao.updateLoaiMon(c, new LoaiMon(input.maLoai(), ten));
                audit.log(c, "SUA", "LOAI_MON", input.maLoai(), ten);
            }
            return null;
        });
    }

    public void deleteLoaiMon(int maLoai) {
        Session.require(Permission.MANAGE_CATALOG);
        db.tx(c -> {
            if (dao.countMonInLoai(c, maLoai) > 0) {
                throw new BusinessException("Không thể xóa loại món đang có món. Hãy chuyển các món sang loại khác trước.");
            }
            dao.deleteLoaiMon(c, maLoai);
            audit.log(c, "XOA", "LOAI_MON", maLoai, null);
            return null;
        });
    }

    // ---------- products ----------
    public List<Mon> findMon() {
        Session.requireLogin();
        return db.read(c -> dao.findMon(c, false));
    }

    public void saveMon(Mon input) {
        Session.require(Permission.MANAGE_CATALOG);
        String ten = Check.required(input.tenMon(), "tên món", 100);
        db.tx(c -> {
            if (dao.monNameExists(c, ten, input.maMon())) {
                throw new BusinessException("Món \"" + ten + "\" đã tồn tại.");
            }
            Mon m = new Mon(input.maMon(), ten, input.maLoai(), null, input.trangThai());
            if (m.maMon() == null) {
                audit.log(c, "THEM", "MON", dao.insertMon(c, m), ten);
            } else {
                dao.updateMon(c, m);
                audit.log(c, "SUA", "MON", m.maMon(), ten + (m.trangThai() ? " (đang bán)" : " (ngừng bán)"));
            }
            return null;
        });
    }

    // ---------- sizes ----------
    public List<Size> findSizes() {
        Session.requireLogin();
        return db.read(dao::findSizes);
    }

    public void saveSize(Size input) {
        Session.require(Permission.MANAGE_CATALOG);
        String ten = Check.required(input.tenSize(), "tên size", 20);
        Check.positive(input.heSoDinhLuong(), "Hệ số định lượng");
        db.tx(c -> {
            if (dao.sizeNameExists(c, ten, input.maSize())) {
                throw new BusinessException("Size \"" + ten + "\" đã tồn tại.");
            }
            Size s = new Size(input.maSize(), ten, input.heSoDinhLuong(), input.thuTu());
            if (s.maSize() == null) {
                audit.log(c, "THEM", "SIZE", dao.insertSize(c, s), ten);
            } else {
                dao.updateSize(c, s);
                audit.log(c, "SUA", "SIZE", s.maSize(), ten);
            }
            return null;
        });
    }

    // ---------- toppings ----------
    public List<Topping> findToppings(boolean activeOnly) {
        Session.requireLogin();
        return db.read(c -> dao.findToppings(c, activeOnly));
    }

    public void saveTopping(Topping input) {
        Session.require(Permission.MANAGE_CATALOG);
        String ten = Check.required(input.tenTopping(), "tên topping", 100);
        Check.nonNegative(input.giaThem(), "Giá thêm");
        db.tx(c -> {
            if (dao.toppingNameExists(c, ten, input.maTopping())) {
                throw new BusinessException("Topping \"" + ten + "\" đã tồn tại.");
            }
            Topping t = new Topping(input.maTopping(), ten, Money.round(input.giaThem()), input.trangThai());
            if (t.maTopping() == null) {
                audit.log(c, "THEM", "TOPPING", dao.insertTopping(c, t), ten + " " + Money.format(t.giaThem()));
            } else {
                dao.updateTopping(c, t);
                audit.log(c, "SUA", "TOPPING", t.maTopping(), ten + " " + Money.format(t.giaThem()));
            }
            return null;
        });
    }

    // ---------- menu for selling ----------
    /** Active products with today's prices. Products without a price in the current list are not sellable. */
    public List<MenuItem> findMenu() {
        Session.require(Permission.SELL);
        return db.read(c -> priceDao.findMenu(c, LocalDate.now()));
    }

    // ---------- price lists ----------
    public List<BangGia> findBangGia() {
        Session.requireLogin();
        return db.read(priceDao::findBangGia);
    }

    public List<ChiTietBangGia> findChiTietBangGia(int maBangGia) {
        Session.requireLogin();
        return db.read(c -> priceDao.findChiTiet(c, maBangGia));
    }

    /**
     * Creates or edits a price list header. A new open-ended list automatically closes the
     * currently running open-ended list the day before it starts; any other overlap is rejected.
     * @param copyFromId for a new list: copy all prices from this list (optional)
     */
    public int saveBangGia(BangGia input, Integer copyFromId) {
        Session.require(Permission.MANAGE_CATALOG);
        String moTa = Check.required(input.moTa(), "mô tả bảng giá", 200);
        LocalDate start = Check.notNull(input.ngayBatDau(), "ngày bắt đầu");
        LocalDate end = input.ngayKetThuc();
        Check.dateRange(start, end, "bảng giá");
        LocalDate today = LocalDate.now();
        return db.tx(c -> {
            BangGia old = input.maBangGia() == null ? null : priceDao.findBangGiaById(c, input.maBangGia());
            if (old == null) {
                if (start.isBefore(today)) {
                    throw new BusinessException("Bảng giá mới phải bắt đầu từ hôm nay trở đi.");
                }
            } else {
                if (old.daHetHieuLuc(today)) {
                    throw new BusinessException("Bảng giá đã hết hiệu lực chỉ được xem, không được sửa.");
                }
                if (!old.ngayBatDau().isAfter(today) && !old.ngayBatDau().equals(start)) {
                    throw new BusinessException("Không thể đổi ngày bắt đầu của bảng giá đã áp dụng.");
                }
                if (old.ngayBatDau().isAfter(today) && start.isBefore(today)) {
                    throw new BusinessException("Ngày bắt đầu không được ở quá khứ.");
                }
                if (end != null && end.isBefore(today.minusDays(1))) {
                    throw new BusinessException("Ngày kết thúc không được trước hôm qua.");
                }
            }
            List<BangGia> overlaps = priceDao.findOverlapping(c, start, end, input.maBangGia());
            if (overlaps.size() == 1 && old == null && end == null) {
                BangGia running = overlaps.get(0);
                if (running.ngayKetThuc() == null && running.ngayBatDau().isBefore(start)) {
                    priceDao.updateBangGia(c, new BangGia(running.maBangGia(), running.moTa(), running.ngayBatDau(), start.minusDays(1)));
                    audit.log(c, "SUA", "BANG_GIA", running.maBangGia(), "Tự động kết thúc ngày " + Dates.format(start.minusDays(1)));
                    overlaps = List.of();
                }
            }
            if (!overlaps.isEmpty()) {
                BangGia o = overlaps.get(0);
                throw new BusinessException("Thời gian áp dụng bị trùng với bảng giá \"" + o.moTa() + "\" ("
                        + Dates.format(o.ngayBatDau()) + " - " + (o.ngayKetThuc() == null ? "không thời hạn" : Dates.format(o.ngayKetThuc())) + ").");
            }
            BangGia bg = new BangGia(input.maBangGia(), moTa, start, end);
            if (old == null) {
                int id = priceDao.insertBangGia(c, bg);
                if (copyFromId != null) {
                    for (ChiTietBangGia ct : priceDao.findChiTiet(c, copyFromId)) {
                        priceDao.insertChiTiet(c, id, ct.maMon(), ct.maSize(), ct.giaBan());
                    }
                }
                audit.log(c, "THEM", "BANG_GIA", id, moTa + (copyFromId != null ? " (sao chép từ bảng giá " + copyFromId + ")" : ""));
                return id;
            }
            priceDao.updateBangGia(c, bg);
            audit.log(c, "SUA", "BANG_GIA", bg.maBangGia(), moTa);
            return bg.maBangGia();
        });
    }

    /** Replaces all prices of a price list. Ended lists are read-only. Already-issued invoices keep their own price snapshot. */
    public void savePrices(int maBangGia, List<PriceEntry> entries) {
        Session.require(Permission.MANAGE_CATALOG);
        Set<String> seen = new HashSet<>();
        for (PriceEntry e : entries) {
            Check.positive(e.giaBan(), "Giá bán");
            if (!seen.add(e.maMon() + "-" + e.maSize())) {
                throw new BusinessException("Một món/size chỉ được có một giá trong cùng bảng giá.");
            }
        }
        db.tx(c -> {
            BangGia bg = priceDao.findBangGiaById(c, maBangGia);
            if (bg == null) {
                throw new BusinessException("Bảng giá không tồn tại.");
            }
            if (bg.daHetHieuLuc(LocalDate.now())) {
                throw new BusinessException("Bảng giá đã hết hiệu lực chỉ được xem, không được sửa.");
            }
            priceDao.deleteChiTiet(c, maBangGia);
            for (PriceEntry e : entries) {
                priceDao.insertChiTiet(c, maBangGia, e.maMon(), e.maSize(), Money.round(e.giaBan()));
            }
            audit.log(c, "CAP_NHAT_GIA", "BANG_GIA", maBangGia, entries.size() + " giá");
            return null;
        });
    }

    public void deleteBangGia(int maBangGia) {
        Session.require(Permission.MANAGE_CATALOG);
        db.tx(c -> {
            BangGia bg = priceDao.findBangGiaById(c, maBangGia);
            if (bg == null) {
                throw new BusinessException("Bảng giá không tồn tại.");
            }
            if (!bg.ngayBatDau().isAfter(LocalDate.now())) {
                throw new BusinessException("Chỉ xóa được bảng giá chưa áp dụng. Bảng giá đã dùng được giữ lại làm lịch sử.");
            }
            priceDao.deleteBangGia(c, maBangGia);
            audit.log(c, "XOA", "BANG_GIA", maBangGia, bg.moTa());
            return null;
        });
    }
}
