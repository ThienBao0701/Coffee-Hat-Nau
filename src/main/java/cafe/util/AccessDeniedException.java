package cafe.util;

/** The logged-in user lacks the permission required for an operation. */
public class AccessDeniedException extends BusinessException {
    public AccessDeniedException(String message) {
        super(message);
    }
}
