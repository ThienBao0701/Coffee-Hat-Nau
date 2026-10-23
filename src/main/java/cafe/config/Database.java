package cafe.config;

import cafe.util.DataAccessException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Opens JDBC connections and runs units of work inside transactions.
 * shortcut: a new connection per unit of work (no pool); fine for one desktop user, add HikariCP for many terminals.
 */
public final class Database {

    private static final Logger LOG = Logger.getLogger(Database.class.getName());

    /** A piece of JDBC work that may throw SQLException. */
    @FunctionalInterface
    public interface Work<T> {
        T run(Connection conn) throws SQLException;
    }

    private final String url;
    private final String user;
    private final String password;

    public Database(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    public static Database fromConfig(AppConfig cfg) {
        return new Database(cfg.get("db.url"), cfg.get("db.user"), cfg.get("db.password"));
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    /** Runs work in one transaction: commit on success, rollback on ANY exception (nothing partial is saved). */
    public <T> T tx(Work<T> work) {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            // Same visibility on H2 and MySQL (whose default REPEATABLE READ would let a read inside a transaction
            // miss a row committed while this transaction waited for a lock, e.g. a second open order on a table).
            conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            try {
                T result = work.run(conn);
                conn.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Database error", e);
            throw new DataAccessException(e);
        }
    }

    /** Read-only work (auto-commit). */
    public <T> T read(Work<T> work) {
        try (Connection conn = getConnection()) {
            return work.run(conn);
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Database error", e);
            throw new DataAccessException(e);
        }
    }

    /**
     * Creates the tables if the database is empty, then optionally loads demo data.
     * An existing database is never dropped, reset or altered.
     * @return true if the schema was created now
     */
    public boolean initializeIfEmpty(boolean seedDemoData) {
        if (tableExists("nhan_vien")) {
            // MySQL commits each CREATE TABLE immediately, so a failed first start can leave a partial schema.
            // nhat_ky is the last table in schema.sql: refuse to run on a half-created database instead of guessing.
            if (!tableExists("nhat_ky")) {
                throw new cafe.util.BusinessException("Cơ sở dữ liệu chỉ có một phần các bảng (lần khởi tạo trước bị lỗi). "
                        + "Hãy tạo lại cơ sở dữ liệu riêng của ứng dụng rồi chạy lại (xem README, mục MySQL).");
            }
            return false;
        }
        LOG.info("Empty database detected - creating schema");
        tx(conn -> {
            runScript(conn, "/db/schema.sql");
            if (seedDemoData) {
                runScript(conn, "/db/seed-demo.sql");
            }
            return null;
        });
        return true;
    }

    /**
     * Applies additive, restart-safe schema upgrades. DDL is intentionally split into individually
     * idempotent steps because MySQL may commit DDL even when a later step fails.
     */
    public void migrate() {
        if (!tableExists("nhan_vien")) {
            return;
        }
        if (!tableExists("nhat_ky")) {
            throw new cafe.util.BusinessException("Cơ sở dữ liệu chưa khởi tạo đầy đủ; chưa áp dụng nâng cấp.");
        }
        tx(conn -> {
            try (Statement st = conn.createStatement()) {
                st.execute("CREATE TABLE IF NOT EXISTS db_schema_version (version INT PRIMARY KEY)");
            }
            if (!JdbcVersion.has(conn, 1)) {
                if (!columnExists(conn.getMetaData(), "nhan_vien", "chu_so_huu")) {
                    try (Statement st = conn.createStatement()) {
                        st.execute("ALTER TABLE nhan_vien ADD COLUMN chu_so_huu BOOLEAN NOT NULL DEFAULT FALSE");
                    }
                }
                if (!columnExists(conn.getMetaData(), "thanh_toan", "ma_ca")) {
                    try (Statement st = conn.createStatement()) {
                        st.execute("ALTER TABLE thanh_toan ADD COLUMN ma_ca INT");
                    }
                }
                runScript(conn, "/db/migration-v1.sql");
                ensureIndex(conn, "ca_thu_ngan", "idx_ca_nv_mo",
                        "CREATE INDEX idx_ca_nv_mo ON ca_thu_ngan (ma_nv, mo_luc)");
                ensureIndex(conn, "bien_dong_tien_mat", "idx_bd_ca_time",
                        "CREATE INDEX idx_bd_ca_time ON bien_dong_tien_mat (ma_ca, thoi_diem)");
                if (!foreignKeyExists(conn.getMetaData(), "thanh_toan", "fk_tt_ca")) {
                    try (Statement st = conn.createStatement()) {
                        st.execute("ALTER TABLE thanh_toan ADD CONSTRAINT fk_tt_ca FOREIGN KEY (ma_ca) REFERENCES ca_thu_ngan (ma_ca)");
                    }
                }
                ensureDrawerRow(conn);
                try (Statement st = conn.createStatement()) {
                    st.executeUpdate("INSERT INTO db_schema_version (version) VALUES (1)");
                }
            }
            if (!JdbcVersion.has(conn, 2)) {
                runScript(conn, "/db/migration-v2.sql");
                ensureIndex(conn, "dieu_chinh_ca", "idx_dc_ca_time",
                        "CREATE INDEX idx_dc_ca_time ON dieu_chinh_ca (ma_ca, thoi_diem)");
                try (Statement st = conn.createStatement()) {
                    st.executeUpdate("INSERT INTO db_schema_version (version) VALUES (2)");
                }
            }
            return null;
        });
    }

    private static boolean columnExists(DatabaseMetaData meta, String table, String column) throws SQLException {
        try (ResultSet rs = meta.getColumns(null, null, null, null)) {
            while (rs.next()) {
                if (table.equalsIgnoreCase(rs.getString("TABLE_NAME"))
                        && column.equalsIgnoreCase(rs.getString("COLUMN_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean foreignKeyExists(DatabaseMetaData meta, String table, String key) throws SQLException {
        try (ResultSet rs = meta.getImportedKeys(null, null, table)) {
            while (rs.next()) {
                if (key.equalsIgnoreCase(rs.getString("FK_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void ensureIndex(Connection c, String table, String index, String ddl) throws SQLException {
        try (ResultSet rs = c.getMetaData().getIndexInfo(null, null, table, false, false)) {
            while (rs.next()) {
                if (index.equalsIgnoreCase(rs.getString("INDEX_NAME"))) {
                    return;
                }
            }
        }
        try (Statement st = c.createStatement()) {
            st.execute(ddl);
        }
    }

    private static void ensureDrawerRow(Connection c) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT 1 FROM ngan_ke_toan WHERE ma_ngan = 1")) {
            if (rs.next()) {
                return;
            }
        }
        try (Statement st = c.createStatement()) {
            st.executeUpdate("INSERT INTO ngan_ke_toan (ma_ngan, ma_ca_dang_mo) VALUES (1, NULL)");
        }
    }

    private static final class JdbcVersion {
        private static boolean has(Connection c, int version) throws SQLException {
            try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM db_schema_version WHERE version = ?")) {
                ps.setInt(1, version);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        }
    }

    private boolean tableExists(String table) {
        try (Connection conn = getConnection(); ResultSet rs = conn.getMetaData().getTables(null, null, null, new String[]{"TABLE"})) {
            while (rs.next()) {
                if (table.equalsIgnoreCase(rs.getString("TABLE_NAME"))) {
                    return true;
                }
            }
            return false;
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Cannot inspect database tables", e);
            throw new DataAccessException(e);
        }
    }

    static void runScript(Connection conn, String resource) throws SQLException {
        try (Statement st = conn.createStatement()) {
            for (String sql : readStatements(resource)) {
                st.execute(sql);
            }
        }
    }

    /** Splits a .sql resource on ';' at end of line, ignoring '--' comment lines. */
    static List<String> readStatements(String resource) {
        String text;
        try (InputStream in = Database.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource " + resource);
            }
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + resource, e);
        }
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                continue;
            }
            current.append(line).append('\n');
            if (trimmed.endsWith(";")) {
                String sql = current.toString().trim();
                statements.add(sql.substring(0, sql.length() - 1));
                current.setLength(0);
            }
        }
        if (!current.toString().isBlank()) {
            statements.add(current.toString().trim());
        }
        return statements;
    }
}
