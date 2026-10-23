package cafe.service;

import cafe.config.Database;
import cafe.dao.CustomerDao;
import cafe.dao.InvoiceDao;
import cafe.dao.TableDao;
import cafe.model.Ban;
import cafe.model.DonDatHang;
import cafe.model.KhuVuc;
import cafe.model.TableStatus;
import cafe.model.TrangThaiBan;
import cafe.util.BusinessException;
import cafe.util.Check;
import cafe.util.Permission;
import cafe.util.Session;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Areas, tables and the live table map. */
public class TableService {

    /** A table shows "Đã đặt" from this long before a reservation until this long after it (if guests have not arrived). */
    static final int RESERVED_BEFORE_MIN = 90;
    static final int RESERVED_AFTER_MIN = 30;

    private final Database db;
    private final AuditService audit;
    private final TableDao dao = new TableDao();
    private final InvoiceDao invoiceDao = new InvoiceDao();
    private final CustomerDao customerDao = new CustomerDao();

    public TableService(Database db, AuditService audit) {
        this.db = db;
        this.audit = audit;
    }

    public List<KhuVuc> findKhuVuc() {
        Session.requireLogin();
        return db.read(dao::findKhuVuc);
    }

    public void saveKhuVuc(KhuVuc input) {
        Session.require(Permission.MANAGE_TABLES);
        String ten = Check.required(input.tenKhuVuc(), "tên khu vực", 100);
        db.tx(c -> {
            if (dao.khuVucNameExists(c, ten, input.maKhuVuc())) {
                throw new BusinessException("Khu vực \"" + ten + "\" đã tồn tại.");
            }
            if (input.maKhuVuc() == null) {
                audit.log(c, "THEM", "KHU_VUC", dao.insertKhuVuc(c, ten), ten);
            } else {
                dao.updateKhuVuc(c, new KhuVuc(input.maKhuVuc(), ten));
                audit.log(c, "SUA", "KHU_VUC", input.maKhuVuc(), ten);
            }
            return null;
        });
    }

    public void deleteKhuVuc(int id) {
        Session.require(Permission.MANAGE_TABLES);
        db.tx(c -> {
            if (dao.countBanInKhuVuc(c, id) > 0) {
                throw new BusinessException("Không thể xóa khu vực đang có bàn.");
            }
            dao.deleteKhuVuc(c, id);
            audit.log(c, "XOA", "KHU_VUC", id, null);
            return null;
        });
    }

    public List<Ban> findBan(boolean activeOnly) {
        Session.requireLogin();
        return db.read(c -> dao.findBan(c, activeOnly));
    }

    public void saveBan(Ban input) {
        Session.require(Permission.MANAGE_TABLES);
        String ten = Check.required(input.tenBan(), "tên bàn", 50);
        Check.positive(input.soCho(), "Số chỗ");
        db.tx(c -> {
            if (dao.banNameExists(c, ten, input.maBan())) {
                throw new BusinessException("Bàn \"" + ten + "\" đã tồn tại.");
            }
            Ban b = new Ban(input.maBan(), ten, input.maKhuVuc(), null, input.soCho(), input.trangThai());
            if (b.maBan() == null) {
                audit.log(c, "THEM", "BAN", dao.insertBan(c, b), ten);
            } else {
                if (!b.trangThai() && invoiceDao.findOpenIdByTable(c, b.maBan()) != null) {
                    throw new BusinessException("Bàn đang có khách, không thể ngừng phục vụ.");
                }
                dao.updateBan(c, b);
                audit.log(c, "SUA", "BAN", b.maBan(), ten + (b.trangThai() ? "" : " (ngừng phục vụ)"));
            }
            return null;
        });
    }

    /** Live table map: status derived from open invoices and upcoming reservations. */
    public List<TableStatus> findTableStatuses() {
        Session.require(Permission.SELL);
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter hhmm = DateTimeFormatter.ofPattern("HH:mm");
        return db.read(c -> {
            Map<Integer, Integer> open = invoiceDao.findOpenByTable(c);
            List<DonDatHang> reservations = customerDao.findActiveReservationsBetween(c,
                    now.minusMinutes(RESERVED_AFTER_MIN), now.plusMinutes(RESERVED_BEFORE_MIN));
            List<TableStatus> result = new ArrayList<>();
            for (Ban b : dao.findBan(c, false)) {
                DonDatHang r = reservations.stream().filter(d -> d.maBan() == b.maBan()).findFirst().orElse(null);
                String note = r == null ? null : "Đặt " + hhmm.format(r.thoiDiemDat()) + " - " + r.tenKH();
                TrangThaiBan st;
                if (!b.trangThai()) {
                    st = TrangThaiBan.NGUNG;
                } else if (open.containsKey(b.maBan())) {
                    st = TrangThaiBan.CO_KHACH;
                } else if (r != null) {
                    st = TrangThaiBan.DA_DAT;
                } else {
                    st = TrangThaiBan.TRONG;
                }
                result.add(new TableStatus(b, st, open.get(b.maBan()), note));
            }
            return result;
        });
    }
}
