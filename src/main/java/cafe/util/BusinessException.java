package cafe.util;

/** A rule or validation failure. The message is Vietnamese and safe to show to the user. */
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}
