package cafe.service;

import cafe.config.Database;
import cafe.dao.CustomerDao;
import cafe.dao.InventoryDao;
import cafe.dao.InvoiceDao;
import cafe.dao.ReportDao;
import cafe.dao.TableDao;
import cafe.model.DonDatHang;
import cafe.model.HoaDon;
import cafe.model.NguyenLieu;
import cafe.model.NhanVien;
import cafe.model.Reports;
import cafe.model.TrangThaiHoaDon;
import cafe.util.Check;
import cafe.util.Permission;
import cafe.util.Session;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Revenue, product, employee and stock reports, plus the dashboard. Definitions: see {@link Reports}. */
public class ReportService {

    /** Dashboard figures. Manager-only fields are null for employees. */
    public record Dashboard(Reports.RevenueSummary today, int myPaidToday, BigDecimal myCollectedToday, int occupiedTables, int activeTables,
                            List<NguyenLieu> lowStock, List<HoaDon> recentInvoices, List<DonDatHang> upcomingReservations,
                            List<Reports.PaymentMethodTotal> paymentMethodsToday) {
    }

    private final Database db;
    private final ReportDao dao = new ReportDao();
    private final InvoiceDao invoiceDao = new InvoiceDao();
    private final TableDao tableDao = new TableDao();
    private final InventoryDao inventoryDao = new InventoryDao();
    private final CustomerDao customerDao = new CustomerDao();

    public ReportService(Database db) {
        this.db = db;
    }

    private static LocalDateTime start(LocalDate d) {
        return d.atStartOfDay();
    }

    private static LocalDateTime endExclusive(LocalDate d) {
        return d.plusDays(1).atStartOfDay();
    }

    public Reports.RevenueSummary summary(LocalDate from, LocalDate to) {
        Session.require(Permission.VIEW_REPORTS);
        Check.dateRange(from, to, "khoảng thời gian");
        return db.read(c -> {
            Reports.RevenueSummary s = summarize(dao.findPaidInvoices(c, start(from), endExclusive(to)),
                    dao.findRefunds(c, start(from), endExclusive(to)));
            return new Reports.RevenueSummary(s.soHoaDon(), s.doanhSo(), s.giamGia(), s.thue(), s.soHoaDonHoan(),
                    s.hoanTien(), s.tongThu(), dao.countCancelled(c, start(from), endExclusive(to)));
        });
    }

    private static Reports.RevenueSummary summarize(List<ReportDao.PaidInvoice> paid, Object[] refunds) {
        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal discount = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal collected = BigDecimal.ZERO;
        for (ReportDao.PaidInvoice p : paid) {
            gross = gross.add(p.tamTinh());
            discount = discount.add(p.tienGiam());
            tax = tax.add(p.tienThue());
            collected = collected.add(p.tongTien());
        }
        return new Reports.RevenueSummary(paid.size(), gross, discount, tax, (Integer) refunds[0], (BigDecimal) refunds[1], collected);
    }

    public List<Reports.RevenueRow> byDay(LocalDate from, LocalDate to) {
        return grouped(from, to, p -> p.thoiDiem().toLocalDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
    }

    public List<Reports.RevenueRow> byMonth(LocalDate from, LocalDate to) {
        return grouped(from, to, p -> p.thoiDiem().format(DateTimeFormatter.ofPattern("MM/yyyy")));
    }

    // shortcut: groups in Java instead of SQL (portable across MySQL/H2); move to GROUP BY if data reaches millions of invoices.
    private List<Reports.RevenueRow> grouped(LocalDate from, LocalDate to, Function<ReportDao.PaidInvoice, String> key) {
        Session.require(Permission.VIEW_REPORTS);
        Check.dateRange(from, to, "khoảng thời gian");
        List<ReportDao.PaidInvoice> paid = db.read(c -> dao.findPaidInvoices(c, start(from), endExclusive(to)));
        Map<String, List<ReportDao.PaidInvoice>> groups = new LinkedHashMap<>();
        for (ReportDao.PaidInvoice p : paid) {
            groups.computeIfAbsent(key.apply(p), k -> new ArrayList<>()).add(p);
        }
        List<Reports.RevenueRow> rows = new ArrayList<>();
        groups.forEach((k, list) -> {
            Reports.RevenueSummary s = summarize(list, new Object[]{0, BigDecimal.ZERO});
            rows.add(new Reports.RevenueRow(k, s.soHoaDon(), s.doanhSo(), s.giamGia(), s.thue(), s.tongThu()));
        });
        return rows;
    }

    public List<Reports.ProductSales> productSales(LocalDate from, LocalDate to) {
        Session.require(Permission.VIEW_REPORTS);
        return db.read(c -> dao.findProductSales(c, start(from), endExclusive(to)));
    }

    public List<Reports.EmployeeSales> employeeSales(LocalDate from, LocalDate to) {
        Session.require(Permission.VIEW_REPORTS);
        List<ReportDao.PaidInvoice> paid = db.read(c -> dao.findPaidInvoices(c, start(from), endExclusive(to)));
        Map<Integer, List<ReportDao.PaidInvoice>> groups = new LinkedHashMap<>();   // by id: two employees may share a name
        for (ReportDao.PaidInvoice p : paid) {
            groups.computeIfAbsent(p.maNV(), k -> new ArrayList<>()).add(p);
        }
        List<Reports.EmployeeSales> rows = new ArrayList<>();
        groups.forEach((id, list) -> {
            String name = list.get(0).tenNV();
            Reports.RevenueSummary s = summarize(list, new Object[]{0, BigDecimal.ZERO});
            rows.add(new Reports.EmployeeSales(id, name, s.soHoaDon(), s.doanhSo().subtract(s.giamGia()), s.tongThu()));
        });
        rows.sort((a, b) -> b.sauGiam().compareTo(a.sauGiam()));
        return rows;
    }

    public List<Reports.PaymentMethodTotal> paymentMethods(LocalDate from, LocalDate to) {
        Session.require(Permission.VIEW_REPORTS);
        Check.dateRange(from, to, "khoảng thời gian");
        return db.read(c -> dao.paymentMethods(c, start(from), endExclusive(to)));
    }

    public List<Reports.CashierSales> cashierSales(LocalDate from, LocalDate to) {
        Session.require(Permission.VIEW_REPORTS);
        Check.dateRange(from, to, "khoảng thời gian");
        return db.read(c -> dao.cashierSales(c, start(from), endExclusive(to)));
    }

    public List<Reports.TransactionRow> transactions(LocalDate from, LocalDate to) {
        return transactions(from, to, null, null);
    }

    public List<Reports.TransactionRow> transactions(LocalDate from, LocalDate to, Integer employeeId, String paymentMethod) {
        Session.require(Permission.VIEW_REPORTS);
        Check.dateRange(from, to, "khoảng thời gian");
        return db.read(c -> dao.transactions(c, start(from), endExclusive(to), employeeId, paymentMethod));
    }

    /** Opening / in / out / closing stock per ingredient, all derived from recorded movements. */
    public List<Reports.StockRow> stock(LocalDate from, LocalDate to) {
        Session.require(Permission.VIEW_REPORTS);
        return db.read(c -> dao.findStockFigures(c, start(from), endExclusive(to))).stream().map(r -> {
            BigDecimal current = (BigDecimal) r[2];
            BigDecimal after = (BigDecimal) r[3];
            BigDecimal in = (BigDecimal) r[4];
            BigDecimal out = ((BigDecimal) r[5]).negate();
            BigDecimal closing = current.subtract(after);
            BigDecimal opening = closing.subtract(in).add(out);
            return new Reports.StockRow((String) r[0], (String) r[1], opening, in, out, closing);
        }).toList();
    }

    public Dashboard dashboard() {
        NhanVien me = Session.requireLogin();
        boolean manager = Session.can(Permission.VIEW_REPORTS);
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        return db.read(c -> {
            int occupied = invoiceDao.findOpenByTable(c).size();
            int active = tableDao.findBan(c, true).size();
            List<DonDatHang> upcoming = customerDao.findActiveReservationsBetween(c, now.minusMinutes(30), endExclusive(today));
            List<HoaDon> todays = invoiceDao.search(c, start(today), endExclusive(today), TrangThaiHoaDon.PAID, manager ? null : me.maNV(), null);
            List<HoaDon> recent = todays.subList(0, Math.min(10, todays.size()));
            if (!manager) {
                Object[] ownCollected = dao.collectedByCashier(c, me.maNV(), start(today), endExclusive(today));
                return new Dashboard(null, (Integer) ownCollected[0], (BigDecimal) ownCollected[1], occupied, active,
                        null, recent, upcoming, List.of());
            }
            Reports.RevenueSummary s = summarize(dao.findPaidInvoices(c, start(today), endExclusive(today)),
                    dao.findRefunds(c, start(today), endExclusive(today)));
            s = new Reports.RevenueSummary(s.soHoaDon(), s.doanhSo(), s.giamGia(), s.thue(), s.soHoaDonHoan(),
                    s.hoanTien(), s.tongThu(), dao.countCancelled(c, start(today), endExclusive(today)));
            return new Dashboard(s, todays.size(), BigDecimal.ZERO, occupied, active, inventoryDao.findLowStock(c), recent, upcoming,
                    dao.paymentMethods(c, start(today), endExclusive(today)));
        });
    }
}
