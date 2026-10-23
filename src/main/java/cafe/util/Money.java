package cafe.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

/**
 * Money helpers. Rounding policy: VND has no subunit, so every computed amount
 * (discount, tax) is rounded to a whole dong with HALF_UP, once, at the step that produces it.
 */
public final class Money {

    private Money() {
    }

    public static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal MAX = new BigDecimal("1000000000000");

    public static BigDecimal round(BigDecimal amount) {
        return amount.setScale(0, RoundingMode.HALF_UP);
    }

    /** amount * percent / 100, rounded to whole dong. */
    public static BigDecimal percentOf(BigDecimal amount, BigDecimal percent) {
        return round(amount.multiply(percent).divide(HUNDRED, 4, RoundingMode.HALF_UP));
    }

    public static String format(BigDecimal amount) {
        if (amount == null) {
            return "";
        }
        return formatter().format(round(amount)) + " đ";
    }

    /** Number without currency suffix, e.g. "25.000". */
    public static String plain(BigDecimal amount) {
        return amount == null ? "" : formatter().format(round(amount));
    }

    /** Parses "25.000", "25000" or "25.000 đ". Returns null for blank input. */
    public static BigDecimal parse(String text, String fieldName) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String digits = text.replace("đ", "").replace(".", "").replace(",", "").replace(" ", "").trim();
        BigDecimal value;
        try {
            value = new BigDecimal(digits);
        } catch (NumberFormatException e) {
            throw new BusinessException(fieldName + " phải là số tiền hợp lệ (ví dụ 25000).");
        }
        if (value.abs().compareTo(MAX) > 0) {   // columns are DECIMAL(15,2)
            throw new BusinessException(fieldName + " quá lớn.");
        }
        return value;
    }

    private static DecimalFormat formatter() {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setGroupingSeparator('.');
        symbols.setDecimalSeparator(',');
        return new DecimalFormat("#,##0", symbols);
    }
}
