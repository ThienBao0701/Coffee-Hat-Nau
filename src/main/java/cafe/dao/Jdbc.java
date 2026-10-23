package cafe.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Small PreparedStatement helper so each DAO method stays a few readable lines. All values are bound as parameters. */
public final class Jdbc {

    private Jdbc() {
    }

    @FunctionalInterface
    public interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    public static <T> List<T> list(Connection conn, String sql, RowMapper<T> mapper, Object... params) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                List<T> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(mapper.map(rs));
                }
                return rows;
            }
        }
    }

    /** First row or null. */
    public static <T> T one(Connection conn, String sql, RowMapper<T> mapper, Object... params) throws SQLException {
        List<T> rows = list(conn, sql, mapper, params);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public static int update(Connection conn, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            return ps.executeUpdate();
        }
    }

    /** Executes an INSERT and returns the generated AUTO_INCREMENT key. */
    public static int insert(Connection conn, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, params);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("No generated key returned");
                }
                return keys.getInt(1);
            }
        }
    }

    public static int count(Connection conn, String sql, Object... params) throws SQLException {
        Integer n = one(conn, sql, rs -> rs.getInt(1), params);
        return n == null ? 0 : n;
    }

    private static void bind(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            Object p = params[i];
            int idx = i + 1;
            if (p instanceof LocalDate d) {
                ps.setDate(idx, Date.valueOf(d));
            } else if (p instanceof LocalDateTime dt) {
                // whole seconds: MySQL DATETIME rounds fractions, which could push 23:59:59.6 into the next day
                ps.setTimestamp(idx, Timestamp.valueOf(dt.withNano(0)));
            } else if (p instanceof Enum<?> e) {
                ps.setString(idx, e.name());
            } else {
                ps.setObject(idx, p);
            }
        }
    }

    public static LocalDate date(ResultSet rs, String col) throws SQLException {
        Date d = rs.getDate(col);
        return d == null ? null : d.toLocalDate();
    }

    public static LocalDateTime dateTime(ResultSet rs, String col) throws SQLException {
        Timestamp t = rs.getTimestamp(col);
        return t == null ? null : t.toLocalDateTime();
    }

    public static Integer intOrNull(ResultSet rs, String col) throws SQLException {
        int v = rs.getInt(col);
        return rs.wasNull() ? null : v;
    }

    public static BigDecimal dec(ResultSet rs, String col) throws SQLException {
        return rs.getBigDecimal(col);
    }

    /** Builds "?, ?, ?" for IN clauses. */
    public static String placeholders(int n) {
        return String.join(", ", java.util.Collections.nCopies(n, "?"));
    }
}
