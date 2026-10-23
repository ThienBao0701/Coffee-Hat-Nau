package cafe.view.catalog;

import cafe.model.Ban;
import cafe.model.KhuVuc;
import cafe.service.Services;
import cafe.view.common.Column;
import cafe.view.common.DataTable;
import cafe.view.common.Dialogs;
import cafe.view.common.Fields;
import cafe.view.common.FormDialog;
import cafe.view.common.ListPanel;
import cafe.view.common.Refreshable;
import cafe.view.common.Theme;
import cafe.view.common.UiTask;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import java.awt.BorderLayout;

/** Areas and tables (manager). */
public class TableAdminPanel extends JPanel implements Refreshable {

    private final Services services;
    private final ListPanel<KhuVuc> areas;
    private final ListPanel<Ban> tables;

    public TableAdminPanel(Services services) {
        super(new BorderLayout());
        this.services = services;
        areas = new ListPanel<>(services.tables::findKhuVuc, new DataTable<>(
                Column.integer("Mã", 50, KhuVuc::maKhuVuc),
                Column.text("Khu vực", 200, KhuVuc::tenKhuVuc)));
        areas.action(Theme.primary("Thêm khu vực"), false, k -> editArea(null));
        areas.action(Theme.button("Sửa"), true, this::editArea);
        areas.action(Theme.danger("Xóa"), true, k -> {
            if (Dialogs.confirm(this, "Xóa khu vực \"" + k.tenKhuVuc() + "\"?")) {
                UiTask.exec(this, () -> services.tables.deleteKhuVuc(k.maKhuVuc()), areas::reload);
            }
        });

        tables = new ListPanel<>(() -> services.tables.findBan(false), new DataTable<>(
                Column.text("Tên bàn", 120, Ban::tenBan),
                Column.text("Khu vực", 140, Ban::tenKhuVuc),
                Column.integer("Số chỗ", 70, Ban::soCho),
                Column.text("Trạng thái", 120, b -> b.trangThai() ? "Đang phục vụ" : "Ngừng phục vụ")));
        tables.action(Theme.primary("Thêm bàn"), false, b -> editTable(null));
        tables.action(Theme.button("Sửa"), true, this::editTable);
        tables.table.onDoubleClick(this::editTable);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, areas, tables);
        split.setResizeWeight(0.35);
        split.setBorder(null);
        add(Theme.screen("Bàn & khu vực", split), BorderLayout.CENTER);
    }

    @Override
    public void reload() {
        areas.reload();
        tables.reload();
    }

    private void editArea(KhuVuc k) {
        FormDialog f = new FormDialog(this, k == null ? "Thêm khu vực" : "Sửa khu vực");
        JTextField ten = Fields.text(k == null ? "" : k.tenKhuVuc());
        f.addRow("Tên khu vực *", ten);
        if (f.open(() -> {
            KhuVuc input = new KhuVuc(k == null ? null : k.maKhuVuc(), ten.getText());
            return () -> services.tables.saveKhuVuc(input);
        })) {
            reload();
        }
    }

    private void editTable(Ban b) {
        UiTask.load(this, services.tables::findKhuVuc, list -> {
            if (list.isEmpty()) {
                Dialogs.info(this, "Hãy tạo khu vực trước.");
                return;
            }
            FormDialog f = new FormDialog(this, b == null ? "Thêm bàn" : "Sửa bàn");
            JTextField ten = Fields.text(b == null ? "" : b.tenBan());
            JComboBox<KhuVuc> kv = Fields.combo(list, b == null ? null : list.stream().filter(x -> x.maKhuVuc() == b.maKhuVuc()).findFirst().orElse(null));
            JTextField soCho = Fields.text(String.valueOf(b == null ? 4 : b.soCho()));
            JCheckBox active = Fields.check("Đang phục vụ", b == null || b.trangThai());
            f.addRow("Tên bàn *", ten).addRow("Khu vực *", kv).addRow("Số chỗ *", soCho).addRow("", active);
            if (f.open(() -> {
                KhuVuc area = (KhuVuc) kv.getSelectedItem();
                Ban input = new Ban(b == null ? null : b.maBan(), ten.getText(), area.maKhuVuc(), null,
                        Fields.getInt(soCho, "Số chỗ"), active.isSelected());
                return () -> services.tables.saveBan(input);
            })) {
                tables.reload();
            }
        });
    }
}
