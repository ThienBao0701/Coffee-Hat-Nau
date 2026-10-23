package cafe.view.common;

import cafe.util.Dates;
import cafe.util.Money;
import cafe.util.Text;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.SwingConstants;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * JTable bound to a List of records: typed columns, sorting, accent-insensitive search,
 * row count / empty-state footer and selection helpers.
 */
public class DataTable<T> extends JPanel {

    private final List<Column<T>> columns;
    private final Model model = new Model();
    private final JTable table;
    private final TableRowSorter<Model> sorter;
    private final CardLayout centerLayout = new CardLayout();
    private final JPanel center = new JPanel(centerLayout);
    private final EmptyState emptyState = new EmptyState("Chưa có dữ liệu", "Danh sách hiện chưa có bản ghi phù hợp.");
    private final JLabel footer = Theme.muted(" ");
    private String emptyText = "Không có dữ liệu";
    private Function<T, Color> rowColor;
    private boolean filterActive;

    @SafeVarargs
    public DataTable(Column<T>... columns) {
        super(new BorderLayout(0, 4));
        setOpaque(false);
        this.columns = List.of(columns);
        table = new JTable(model) {
            @Override
            public String getToolTipText(MouseEvent event) {
                Point point = event.getPoint();
                int viewRow = rowAtPoint(point);
                int viewColumn = columnAtPoint(point);
                if (viewRow < 0 || viewColumn < 0) return null;
                int modelRow = convertRowIndexToModel(viewRow);
                int modelColumn = convertColumnIndexToModel(viewColumn);
                Object value = model.getValueAt(modelRow, modelColumn);
                String display = format(DataTable.this.columns.get(modelColumn).format(), value);
                int width = getColumnModel().getColumn(viewColumn).getWidth();
                return !display.isBlank() && getFontMetrics(getFont()).stringWidth(display) > width - 22 ? display : null;
            }
        };
        table.setToolTipText("");
        sorter = new TableRowSorter<>(model);
        table.setRowSorter(sorter);
        table.setFont(Theme.BASE);
        table.setRowHeight(42);
        table.setFillsViewportHeight(true);
        table.setShowGrid(true);
        table.setShowVerticalLines(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setGridColor(Theme.BORDER);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setFont(Theme.BOLD);
        table.getTableHeader().setPreferredSize(new Dimension(0, 40));
        table.getTableHeader().setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER));
        table.setAutoCreateRowSorter(false);
        for (int i = 0; i < columns.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(columns[i].width());
            table.getColumnModel().getColumn(i).setCellRenderer(new Renderer(columns[i].format(), isStatusColumn(columns[i].name())));
        }
        JScrollPane scroll = new JScrollPane(table);
        Theme.styleScrollPane(scroll);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(Theme.SURFACE);
        center.setOpaque(false);
        center.add(scroll, "rows");
        center.add(emptyState, "empty");
        add(center, BorderLayout.CENTER);
        footer.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 0));
        add(footer, BorderLayout.SOUTH);
        updateFooter();
    }

    public JTable table() {
        return table;
    }

    public void setEmptyText(String text) {
        this.emptyText = text;
        replaceEmptyState();
        updateFooter();
    }

    /** Optional background colour per row (e.g. low stock). */
    public void setRowColor(Function<T, Color> rowColor) {
        this.rowColor = rowColor;
    }

    public void setRows(List<T> rows) {
        T selected = getSelected();
        model.rows = new ArrayList<>(rows);
        model.fireTableDataChanged();
        if (selected != null) {
            int idx = model.rows.indexOf(selected);
            if (idx >= 0 && sorter.convertRowIndexToView(idx) >= 0) {
                int v = sorter.convertRowIndexToView(idx);
                table.setRowSelectionInterval(v, v);
            }
        }
        updateFooter();
    }

    public List<T> getRows() {
        return model.rows;
    }

    public T getSelected() {
        int v = table.getSelectedRow();
        return v < 0 ? null : model.rows.get(table.convertRowIndexToModel(v));
    }

    /** Filters rows whose text contains the keyword (ignores case and Vietnamese accents). */
    public void setFilter(String keyword) {
        String k = Text.fold(keyword).trim();
        filterActive = !k.isEmpty();
        if (k.isEmpty()) {
            sorter.setRowFilter(null);
        } else {
            sorter.setRowFilter(new RowFilter<>() {
                @Override
                public boolean include(Entry<? extends Model, ? extends Integer> entry) {
                    for (int i = 0; i < columns.size(); i++) {
                        if (Text.fold(format(columns.get(i).format(), entry.getValue(i))).contains(k)) {
                            return true;
                        }
                    }
                    return false;
                }
            });
        }
        updateFooter();
    }

    public void onDoubleClick(Consumer<T> action) {
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && table.rowAtPoint(e.getPoint()) >= 0) {
                    T row = getSelected();
                    if (row != null) {
                        action.accept(row);
                    }
                }
            }
        });
    }

    /** Single mouse click on a row (not fired by programmatic selection, unlike onSelect). */
    public void onClick(Consumer<T> action) {
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                T row = table.rowAtPoint(e.getPoint()) >= 0 ? getSelected() : null;
                if (row != null) {
                    action.accept(row);
                }
            }
        });
    }

    public void onSelect(Consumer<T> action) {
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                action.accept(getSelected());
            }
        });
    }

    private void updateFooter() {
        int shown = table.getRowCount();
        if (shown == 0) {
            centerLayout.show(center, "empty");
            footer.setVisible(false);
            replaceEmptyState();
        } else {
            centerLayout.show(center, "rows");
            footer.setVisible(true);
            footer.setText(shown + " dòng");
        }
    }

    private void replaceEmptyState() {
        emptyState.setContent(filterActive ? "Không tìm thấy kết quả" : emptyText,
                filterActive ? "Thử đổi từ khóa hoặc điều kiện lọc để xem thêm dữ liệu."
                        : "Danh sách hiện chưa có bản ghi phù hợp.");
    }

    private static boolean isStatusColumn(String name) {
        String folded = cafe.util.Text.fold(name).toLowerCase(java.util.Locale.ROOT);
        return folded.contains("trang thai") || folded.equals("tai khoan") || folded.equals("tinh trang");
    }

    static String format(Column.Format f, Object v) {
        if (v == null) {
            return "";
        }
        return switch (f) {
            case MONEY -> Money.format((BigDecimal) v);
            case QTY -> ((BigDecimal) v).stripTrailingZeros().toPlainString();
            case DATE -> Dates.format((LocalDate) v);
            case DATETIME -> Dates.format((LocalDateTime) v);
            default -> String.valueOf(v);
        };
    }

    private final class Model extends AbstractTableModel {
        List<T> rows = new ArrayList<>();

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return columns.size();
        }

        @Override
        public String getColumnName(int c) {
            return columns.get(c).name();
        }

        @Override
        public Class<?> getColumnClass(int c) {
            return columns.get(c).format().type;
        }

        @Override
        public Object getValueAt(int r, int c) {
            Object v = columns.get(c).value().apply(rows.get(r));
            return columns.get(c).format() == Column.Format.TEXT && v != null ? String.valueOf(v) : v;
        }
    }

    private final class Renderer extends DefaultTableCellRenderer {
        private final Column.Format format;
        private final boolean status;
        private Color badgeColor;

        Renderer(Column.Format format, boolean status) {
            this.format = format;
            this.status = status;
            setHorizontalAlignment(status ? SwingConstants.CENTER
                    : format == Column.Format.MONEY || format == Column.Format.QTY || format == Column.Format.INT
                    ? SwingConstants.RIGHT : SwingConstants.LEFT);
        }

        @Override
        public Component getTableCellRendererComponent(JTable t, Object value, boolean selected, boolean focus, int row, int col) {
            super.getTableCellRendererComponent(t, format(format, value), selected, false, row, col);
            setBorder(BorderFactory.createEmptyBorder(0, status ? 5 : 7, 0, status ? 5 : 7));
            if (status) {
                Color[] colors = statusColors(String.valueOf(value));
                setForeground(colors[0]);
                badgeColor = colors[1];
                setFont(Theme.font(Font.BOLD, 12));
                setOpaque(false);
            } else {
                badgeColor = null;
                setOpaque(true);
            }
            if (!selected) {
                Color c = null;
                if (rowColor != null) {
                    c = rowColor.apply(model.rows.get(t.convertRowIndexToModel(row)));
                }
                setBackground(c != null ? c : (row % 2 == 0 ? Theme.SURFACE : Theme.ROW_ALT));
            }
            return this;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            if (status && badgeColor != null && getText() != null && !getText().isBlank()) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int textWidth = getFontMetrics(getFont()).stringWidth(getText());
                int badgeWidth = Math.min(getWidth() - 8, textWidth + 18);
                int x = Math.max(4, (getWidth() - badgeWidth) / 2);
                int height = Math.min(24, getHeight() - 6);
                g.setColor(badgeColor);
                g.fillRoundRect(x, (getHeight() - height) / 2, badgeWidth, height, 10, 10);
                g.dispose();
            }
            super.paintComponent(graphics);
        }

        private Color[] statusColors(String value) {
            String folded = cafe.util.Text.fold(value).toLowerCase(java.util.Locale.ROOT);
            if (folded.contains("hoat dong") || folded.contains("dang mo") || folded.contains("da thanh toan")
                    || folded.contains("da xac nhan") || folded.equals("trong") || folded.contains("da nhan ban")) {
                return new Color[]{new Color(0x36553F), new Color(0xE7F0E8)};
            }
            if (folded.contains("cho") || folded.contains("da dat") || folded.contains("sap het") || folded.contains("canh bao")) {
                return new Color[]{new Color(0x79500F), new Color(0xFBF2E0)};
            }
            if (folded.contains("huy") || folded.contains("khoa") || folded.contains("ngung") || folded.contains("tu choi")) {
                return new Color[]{new Color(0x963D38), new Color(0xF8E9E7)};
            }
            if (folded.contains("hoan tien")) {
                return new Color[]{new Color(0x3F657A), new Color(0xE9F0F4)};
            }
            return new Color[]{Theme.MUTED, new Color(0xF1EEEA)};
        }
    }
}
