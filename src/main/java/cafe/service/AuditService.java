package cafe.service;

import cafe.config.Database;
import cafe.dao.AuditDao;
import cafe.model.NhanVien;
import cafe.model.NhatKy;
import cafe.util.Permission;
import cafe.util.Session;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Logger;

/** Records who did what and when. Written inside the same transaction as the change it describes. */
public class AuditService {

    private static final Logger LOG = Logger.getLogger(AuditService.class.getName());

    private final Database db;
    private final AuditDao dao = new AuditDao();

    public AuditService(Database db) {
        this.db = db;
    }

    public void log(Connection c, String action, String entity, Object id, String detail) throws SQLException {
        NhanVien u = Session.current();
        Integer maNV = u == null ? null : u.maNV();
        dao.insert(c, LocalDateTime.now(), maNV, action, entity, id == null ? null : String.valueOf(id), truncate(detail));
        LOG.fine(() -> action + " " + entity + " " + id + " by " + (u == null ? "-" : u.tenDangNhap()));
    }

    public List<NhatKy> find(LocalDate from, LocalDate to) {
        Session.require(Permission.VIEW_AUDIT_LOG);
        return db.read(c -> dao.find(c, from.atStartOfDay(), to.plusDays(1).atStartOfDay()));
    }

    private static String truncate(String s) {
        return s == null || s.length() <= 500 ? s : s.substring(0, 497) + "...";
    }
}
