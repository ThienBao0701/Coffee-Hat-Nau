package cafe.service;

import cafe.config.AppConfig;
import cafe.config.Database;
import cafe.dao.NhanVienDao;
import cafe.util.Session;

/** Creates every service once and wires their dependencies (no DI framework needed). */
public final class Services {

    public final AppConfig config;
    public final Database db;
    public final AuditService audit;
    public final AuthService auth;
    public final EmployeeService employees;
    public final CatalogService catalog;
    public final TableService tables;
    public final CustomerService customers;
    public final PromotionService promotions;
    public final InventoryService inventory;
    public final OrderService orders;
    public final CashShiftService cashShifts;
    public final ReportService reports;

    public Services(AppConfig config, Database db) {
        this.config = config;
        this.db = db;
        audit = new AuditService(db);
        auth = new AuthService(db, audit, Boolean.getBoolean("cafe.owner.bootstrap"));
        employees = new EmployeeService(db, audit);
        catalog = new CatalogService(db, audit);
        tables = new TableService(db, audit);
        customers = new CustomerService(db, audit, config.getInt("reservation.durationMinutes"));
        promotions = new PromotionService(db, audit);
        inventory = new InventoryService(db, audit, config.getBool("inventory.allowNegativeStockOnSale"));
        cashShifts = new CashShiftService(db, audit);
        orders = new OrderService(db, audit, inventory);
        reports = new ReportService(db);
        NhanVienDao accounts = new NhanVienDao();
        Session.setLoader(id -> db.read(c -> accounts.findById(c, id)));
    }
}
