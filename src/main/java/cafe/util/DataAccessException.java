package cafe.util;

import java.sql.SQLException;

/** Wraps SQLException. The UI shows a generic message; details only go to the log. */
public class DataAccessException extends RuntimeException {
    public DataAccessException(SQLException cause) {
        super(cause);
    }

    /** True if the database rejected the change because of a UNIQUE / FOREIGN KEY / CHECK constraint. */
    public boolean isConstraintViolation() {
        String state = ((SQLException) getCause()).getSQLState();
        return state != null && state.startsWith("23");
    }
}
