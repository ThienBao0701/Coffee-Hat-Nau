package cafe.util;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.regex.Pattern;

/** Shared validation rules. Each method throws BusinessException with a Vietnamese message. */
public final class Check {

    private Check() {
    }

    private static final Pattern PHONE = Pattern.compile("^0\\d{9,10}$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern CMND = Pattern.compile("^(\\d{9}|\\d{12})$");
    private static final Pattern USERNAME = Pattern.compile("^[a-zA-Z0-9_.]{3,50}$");

    /** Trims and requires a non-empty value of at most maxLen characters. */
    public static String required(String value, String field, int maxLen) {
        String v = optional(value, field, maxLen);
        if (v == null) {
            throw new BusinessException("Vui lòng nhập " + field + ".");
        }
        return v;
    }

    /** Trims; blank becomes null. */
    public static String optional(String value, String field, int maxLen) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.trim();
        if (v.length() > maxLen) {
            throw new BusinessException(field + " không được dài quá " + maxLen + " ký tự.");
        }
        return v;
    }

    public static <T> T notNull(T value, String field) {
        if (value == null) {
            throw new BusinessException("Vui lòng chọn/nhập " + field + ".");
        }
        return value;
    }

    public static String phone(String value, boolean required) {
        String v = required ? required(value, "số điện thoại", 15) : optional(value, "Số điện thoại", 15);
        if (v != null && !PHONE.matcher(v.replace(" ", "")).matches()) {
            throw new BusinessException("Số điện thoại không hợp lệ (10-11 chữ số, bắt đầu bằng 0).");
        }
        return v == null ? null : v.replace(" ", "");
    }

    public static String email(String value) {
        String v = optional(value, "Email", 100);
        if (v != null && !EMAIL.matcher(v).matches()) {
            throw new BusinessException("Email không hợp lệ.");
        }
        return v;
    }

    public static String cmnd(String value) {
        String v = optional(value, "Số CMND/CCCD", 20);
        if (v != null && !CMND.matcher(v).matches()) {
            throw new BusinessException("Số CMND/CCCD phải gồm 9 hoặc 12 chữ số.");
        }
        return v;
    }

    public static String username(String value) {
        String v = required(value, "tên đăng nhập", 50);
        if (!USERNAME.matcher(v).matches()) {
            throw new BusinessException("Tên đăng nhập chỉ gồm chữ không dấu, số, '_' hoặc '.', từ 3 đến 50 ký tự.");
        }
        return v;
    }

    public static void password(char[] password) {
        if (password == null || password.length < 8) {
            throw new BusinessException("Mật khẩu phải có ít nhất 8 ký tự.");
        }
        boolean letter = false;
        boolean digit = false;
        for (char c : password) {
            letter |= Character.isLetter(c);
            digit |= Character.isDigit(c);
        }
        if (!letter || !digit) {
            throw new BusinessException("Mật khẩu phải có cả chữ và số.");
        }
    }

    public static BigDecimal positive(BigDecimal value, String field) {
        notNull(value, field);
        if (value.signum() <= 0) {
            throw new BusinessException(field + " phải lớn hơn 0.");
        }
        return value;
    }

    public static BigDecimal nonNegative(BigDecimal value, String field) {
        notNull(value, field);
        if (value.signum() < 0) {
            throw new BusinessException(field + " không được âm.");
        }
        return value;
    }

    public static int positive(int value, String field) {
        if (value <= 0) {
            throw new BusinessException(field + " phải lớn hơn 0.");
        }
        return value;
    }

    public static void dateRange(LocalDate from, LocalDate to, String what) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new BusinessException("Ngày kết thúc của " + what + " phải sau hoặc bằng ngày bắt đầu.");
        }
    }
}
