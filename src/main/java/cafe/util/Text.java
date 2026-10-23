package cafe.util;

import java.text.Normalizer;
import java.util.Locale;

public final class Text {

    private Text() {
    }

    /** Lower-case and strip Vietnamese diacritics so "ca phe" matches "Cà phê". */
    public static String fold(String s) {
        if (s == null) {
            return "";
        }
        String noMarks = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return noMarks.replace('đ', 'd').replace('Đ', 'D').toLowerCase(Locale.ROOT);
    }

    /** Shows only the last 4 characters, e.g. ********1234. */
    public static String mask(String s) {
        if (s == null || s.length() <= 4) {
            return s == null ? "" : s;
        }
        return "*".repeat(s.length() - 4) + s.substring(s.length() - 4);
    }
}
