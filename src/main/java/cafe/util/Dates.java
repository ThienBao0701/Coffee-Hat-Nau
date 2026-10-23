package cafe.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

/** Vietnamese date presentation: dd/MM/yyyy and dd/MM/yyyy HH:mm. */
public final class Dates {

    private Dates() {
    }

    public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);
    public static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm").withResolverStyle(ResolverStyle.STRICT);

    public static String format(LocalDate d) {
        return d == null ? "" : DATE.format(d);
    }

    public static String format(LocalDateTime dt) {
        return dt == null ? "" : DATE_TIME.format(dt);
    }

    public static LocalDate parseDate(String text, String fieldName) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(text.trim(), DATE);
        } catch (DateTimeParseException e) {
            throw new BusinessException(fieldName + " không hợp lệ. Định dạng: dd/MM/yyyy");
        }
    }

    public static LocalDateTime parseDateTime(String text, String fieldName) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(text.trim(), DATE_TIME);
        } catch (DateTimeParseException e) {
            throw new BusinessException(fieldName + " không hợp lệ. Định dạng: dd/MM/yyyy HH:mm");
        }
    }
}
