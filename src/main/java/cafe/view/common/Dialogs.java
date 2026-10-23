package cafe.view.common;

import cafe.util.BusinessException;
import cafe.util.DataAccessException;

import javax.swing.JOptionPane;
import java.awt.Component;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Message boxes in Vietnamese. Technical details go to the log, never to the screen. */
public final class Dialogs {

    private static final Logger LOG = Logger.getLogger(Dialogs.class.getName());

    private Dialogs() {
    }

    public static void info(Component owner, String message) {
        JOptionPane.showMessageDialog(owner, message, "Thông báo", JOptionPane.INFORMATION_MESSAGE);
    }

    public static boolean confirm(Component owner, String message) {
        return JOptionPane.showConfirmDialog(owner, message, "Xác nhận", JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
    }

    /** Asks for a mandatory reason (cancel, refund...). Returns null if the user closes the dialog. */
    public static String askReason(Component owner, String title, String prompt) {
        while (true) {
            String s = (String) JOptionPane.showInputDialog(owner, prompt, title, JOptionPane.QUESTION_MESSAGE, null, null, "");
            if (s == null) {
                return null;
            }
            if (!s.isBlank()) {
                return s.trim();
            }
            JOptionPane.showMessageDialog(owner, "Vui lòng nhập lý do.", title, JOptionPane.WARNING_MESSAGE);
        }
    }

    /** Text safe to show to the user for any exception. */
    public static String userMessage(Throwable e) {
        if (e instanceof BusinessException) {
            return e.getMessage();
        }
        if (e instanceof DataAccessException dae) {
            return dae.isConstraintViolation()
                    ? "Dữ liệu bị trùng hoặc đang được sử dụng ở nơi khác. Vui lòng kiểm tra lại."
                    : "Không thể truy cập cơ sở dữ liệu. Vui lòng thử lại hoặc liên hệ quản lý.";
        }
        LOG.log(Level.SEVERE, "Unexpected error", e);
        return "Đã xảy ra lỗi không mong muốn. Vui lòng thử lại hoặc liên hệ quản lý.";
    }

    public static void error(Component owner, Throwable e) {
        int type = e instanceof BusinessException ? JOptionPane.WARNING_MESSAGE : JOptionPane.ERROR_MESSAGE;
        JOptionPane.showMessageDialog(owner, userMessage(e), "Không thực hiện được", type);
    }
}
