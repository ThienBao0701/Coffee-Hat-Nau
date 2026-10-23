package cafe.view.common;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.function.Function;

/** A table column: header, preferred width, display format and how to read the value from a row. */
public record Column<T>(String name, int width, Format format, Function<T, ?> value) {

    public enum Format {
        TEXT(String.class), INT(Integer.class), MONEY(BigDecimal.class), QTY(BigDecimal.class),
        DATE(LocalDate.class), DATETIME(LocalDateTime.class);

        final Class<?> type;

        Format(Class<?> type) {
            this.type = type;
        }
    }

    public static <T> Column<T> text(String name, int width, Function<T, ?> value) {
        return new Column<>(name, width, Format.TEXT, value);
    }

    public static <T> Column<T> integer(String name, int width, Function<T, Integer> value) {
        return new Column<>(name, width, Format.INT, value);
    }

    public static <T> Column<T> money(String name, int width, Function<T, BigDecimal> value) {
        return new Column<>(name, width, Format.MONEY, value);
    }

    public static <T> Column<T> qty(String name, int width, Function<T, BigDecimal> value) {
        return new Column<>(name, width, Format.QTY, value);
    }

    public static <T> Column<T> date(String name, int width, Function<T, LocalDate> value) {
        return new Column<>(name, width, Format.DATE, value);
    }

    public static <T> Column<T> dateTime(String name, int width, Function<T, LocalDateTime> value) {
        return new Column<>(name, width, Format.DATETIME, value);
    }
}
