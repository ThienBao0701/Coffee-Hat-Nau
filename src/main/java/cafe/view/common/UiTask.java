package cafe.view.common;

import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.Component;
import java.awt.Cursor;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/**
 * Runs database work off the Event Dispatch Thread and delivers the result back on it.
 * Errors are shown with {@link Dialogs#error} unless an onError handler is given.
 */
public final class UiTask {

    private UiTask() {
    }

    /** Background call that returns a value. */
    public static <T> void load(Component owner, Callable<T> work, Consumer<T> onSuccess) {
        load(owner, work, onSuccess, null);
    }

    public static <T> void load(Component owner, Callable<T> work, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        Component root = owner == null ? null : SwingUtilities.getRoot(owner);
        if (root != null) {
            root.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        }
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception {
                return work.call();
            }

            @Override
            protected void done() {
                if (root != null) {
                    root.setCursor(Cursor.getDefaultCursor());
                }
                T result;
                try {
                    result = get();
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    if (onError != null) {
                        onError.accept(cause);
                    } else {
                        Dialogs.error(owner, cause);
                    }
                    return;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                if (onSuccess != null) {
                    onSuccess.accept(result);
                }
            }
        }.execute();
    }

    /** Background action without a result. */
    public static void exec(Component owner, Runnable work, Runnable onSuccess) {
        load(owner, () -> {
            work.run();
            return Boolean.TRUE;
        }, ok -> {
            if (onSuccess != null) {
                onSuccess.run();
            }
        });
    }
}
