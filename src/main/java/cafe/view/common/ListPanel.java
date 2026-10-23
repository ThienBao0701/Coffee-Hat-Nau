package cafe.view.common;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * Standard list screen: toolbar (actions on the left, search on the right) above a DataTable.
 * Rows are loaded in the background by {@code loader}; call {@link #reload()} after any change.
 */
public class ListPanel<T> extends JPanel implements Refreshable {

    public final DataTable<T> table;
    private final JPanel actions = Fields.row();
    private final JPanel right = Fields.row();
    private final Callable<List<T>> loader;

    public ListPanel(Callable<List<T>> loader, DataTable<T> table) {
        super(new BorderLayout(0, 8));
        setOpaque(false);
        setBorder(Theme.pad(10, 10));
        this.loader = loader;
        this.table = table;
        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.setOpaque(false);
        toolbar.add(actions, BorderLayout.WEST);
        right.add(new JLabel("Tìm:"));
        right.add(Fields.search(table::setFilter));
        toolbar.add(right, BorderLayout.EAST);
        add(toolbar, BorderLayout.NORTH);
        add(table, BorderLayout.CENTER);
    }

    /** Adds a toolbar button. If needsRow, the action receives the selected row (and asks to pick one if none). */
    public JButton action(JButton b, boolean needsRow, Consumer<T> handler) {
        b.addActionListener(e -> {
            T row = table.getSelected();
            if (needsRow && row == null) {
                Dialogs.info(this, "Vui lòng chọn một dòng trong danh sách.");
                return;
            }
            handler.accept(row);
        });
        actions.add(b);
        return b;
    }

    /** Extra component next to the search box (e.g. a filter). */
    public void addFilter(Component c) {
        right.add(c, 0);
    }

    @Override
    public void reload() {
        UiTask.load(this, loader, table::setRows);
    }
}
