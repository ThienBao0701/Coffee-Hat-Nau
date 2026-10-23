package cafe.view.catalog;

import cafe.model.BangGia;
import cafe.model.ChiTietBangGia;
import cafe.model.LoaiMon;
import cafe.model.Mon;
import cafe.model.Size;
import cafe.model.Topping;
import cafe.service.CatalogService;
import cafe.service.Services;
import cafe.util.BusinessException;
import cafe.util.Money;
import cafe.view.common.Column;
import cafe.view.common.DataTable;
import cafe.view.common.Dialogs;
import cafe.view.common.Fields;
import cafe.view.common.FormDialog;
import cafe.view.common.ListPanel;
import cafe.view.common.Refreshable;
import cafe.view.common.Theme;
import cafe.view.common.UiTask;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Products, categories, sizes, toppings and price lists (manager). */
public class CatalogPanel extends JPanel implements Refreshable {

    private final Services services;
    private final JTabbedPane tabs = new JTabbedPane();
    private final ListPanel<Mon> products;
    private final ListPanel<LoaiMon> categories;
    private final ListPanel<Size> sizes;
    private final ListPanel<Topping> toppings;
    private final PriceListTab priceLists;

    public CatalogPanel(Services services) {
        super(new BorderLayout());
        this.services = services;

        products = new ListPanel<>(services.catalog::findMon, new DataTable<>(
                Column.integer("Mã", 50, Mon::maMon),
                Column.text("Tên món", 220, Mon::tenMon),
                Column.text("Loại", 140, Mon::tenLoai),
                Column.text("Trạng thái", 110, m -> m.trangThai() ? "Đang bán" : "Ngừng bán")));
        products.table.setRowColor(m -> m.trangThai() ? null : new java.awt.Color(0xF1ECE7));
        products.action(Theme.primary("Thêm món"), false, m -> editMon(null));
        products.action(Theme.button("Sửa"), true, this::editMon);
        products.action(Theme.button("Ngừng / mở bán"), true, m -> {
            String q = m.trangThai() ? "Ngừng bán \"" + m.tenMon() + "\"? Món sẽ không xuất hiện khi gọi món mới (hóa đơn cũ vẫn giữ nguyên)."
                    : "Mở bán lại \"" + m.tenMon() + "\"?";
            if (Dialogs.confirm(this, q)) {
                Mon changed = new Mon(m.maMon(), m.tenMon(), m.maLoai(), m.tenLoai(), !m.trangThai());
                UiTask.exec(this, () -> services.catalog.saveMon(changed), products::reload);
            }
        });
        products.table.onDoubleClick(this::editMon);

        categories = new ListPanel<>(services.catalog::findLoaiMon, new DataTable<>(
                Column.integer("Mã", 50, LoaiMon::maLoai),
                Column.text("Tên loại món", 260, LoaiMon::tenLoai)));
        categories.action(Theme.primary("Thêm loại"), false, l -> editLoai(null));
        categories.action(Theme.button("Sửa"), true, this::editLoai);
        categories.action(Theme.danger("Xóa"), true, l -> {
            if (Dialogs.confirm(this, "Xóa loại món \"" + l.tenLoai() + "\"?")) {
                UiTask.exec(this, () -> services.catalog.deleteLoaiMon(l.maLoai()), categories::reload);
            }
        });

        sizes = new ListPanel<>(services.catalog::findSizes, new DataTable<>(
                Column.text("Size", 80, Size::tenSize),
                Column.qty("Hệ số định lượng", 130, Size::heSoDinhLuong),
                Column.integer("Thứ tự", 70, Size::thuTu)));
        sizes.action(Theme.primary("Thêm size"), false, s -> editSize(null));
        sizes.action(Theme.button("Sửa"), true, this::editSize);

        toppings = new ListPanel<>(() -> services.catalog.findToppings(false), new DataTable<>(
                Column.text("Topping", 200, Topping::tenTopping),
                Column.money("Giá thêm", 110, Topping::giaThem),
                Column.text("Trạng thái", 110, t -> t.trangThai() ? "Đang phục vụ" : "Ngừng")));
        toppings.action(Theme.primary("Thêm topping"), false, t -> editTopping(null));
        toppings.action(Theme.button("Sửa"), true, this::editTopping);
        toppings.table.onDoubleClick(this::editTopping);

        priceLists = new PriceListTab();

        tabs.addTab("Món", products);
        tabs.addTab("Loại món", categories);
        tabs.addTab("Size", sizes);
        tabs.addTab("Topping", toppings);
        tabs.addTab("Bảng giá", priceLists);
        tabs.addChangeListener(e -> reload());
        add(Theme.screen("Thực đơn & giá", tabs), BorderLayout.CENTER);
    }

    @Override
    public void reload() {
        ((Refreshable) tabs.getSelectedComponent()).reload();
    }

    private void editMon(Mon m) {
        UiTask.load(this, services.catalog::findLoaiMon, cats -> {
            if (cats.isEmpty()) {
                Dialogs.info(this, "Hãy tạo loại món trước.");
                return;
            }
            FormDialog f = new FormDialog(this, m == null ? "Thêm món" : "Sửa món");
            JTextField ten = Fields.text(m == null ? "" : m.tenMon());
            JComboBox<LoaiMon> loai = Fields.combo(cats, m == null ? null : cats.stream().filter(c -> c.maLoai() == m.maLoai()).findFirst().orElse(null));
            JCheckBox active = Fields.check("Đang bán", m == null || m.trangThai());
            f.addRow("Tên món *", ten).addRow("Loại món *", loai).addRow("", active);
            if (m == null) {
                f.addWide(Theme.muted("Sau khi thêm, hãy nhập giá bán cho món trong tab \"Bảng giá\"."));
            }
            if (f.open(() -> {
                LoaiMon l = (LoaiMon) loai.getSelectedItem();
                Mon input = new Mon(m == null ? null : m.maMon(), ten.getText(), l.maLoai(), null, active.isSelected());
                return () -> services.catalog.saveMon(input);
            })) {
                products.reload();
            }
        });
    }

    private void editLoai(LoaiMon l) {
        FormDialog f = new FormDialog(this, l == null ? "Thêm loại món" : "Sửa loại món");
        JTextField ten = Fields.text(l == null ? "" : l.tenLoai());
        f.addRow("Tên loại *", ten);
        if (f.open(() -> {
            LoaiMon input = new LoaiMon(l == null ? null : l.maLoai(), ten.getText());
            return () -> services.catalog.saveLoaiMon(input);
        })) {
            categories.reload();
        }
    }

    private void editSize(Size s) {
        FormDialog f = new FormDialog(this, s == null ? "Thêm size" : "Sửa size");
        JTextField ten = Fields.text(s == null ? "" : s.tenSize());
        JTextField heSo = Fields.decimal(s == null ? BigDecimal.ONE : s.heSoDinhLuong());
        JTextField thuTu = Fields.text(String.valueOf(s == null ? 0 : s.thuTu()));
        f.addRow("Tên size *", ten).addRow("Hệ số định lượng *", heSo).addRow("Thứ tự hiển thị", thuTu);
        f.addWide(Theme.muted("Hệ số nhân với định lượng công thức (size M = 1)."));
        if (f.open(() -> {
            Size input = new Size(s == null ? null : s.maSize(), ten.getText(), Fields.getDecimal(heSo, "Hệ số định lượng"),
                    Fields.getInt(thuTu, "Thứ tự"));
            return () -> services.catalog.saveSize(input);
        })) {
            sizes.reload();
        }
    }

    private void editTopping(Topping t) {
        FormDialog f = new FormDialog(this, t == null ? "Thêm topping" : "Sửa topping");
        JTextField ten = Fields.text(t == null ? "" : t.tenTopping());
        JTextField gia = Fields.money(t == null ? null : t.giaThem());
        JCheckBox active = Fields.check("Đang phục vụ", t == null || t.trangThai());
        f.addRow("Tên topping *", ten).addRow("Giá thêm (VNĐ) *", gia).addRow("", active);
        if (f.open(() -> {
            Topping input = new Topping(t == null ? null : t.maTopping(), ten.getText(), Fields.getMoney(gia, "Giá thêm"), active.isSelected());
            return () -> services.catalog.saveTopping(input);
        })) {
            toppings.reload();
        }
    }

    /** Price lists on the left, an editable product x size price grid for the selected list on the right. */
    private final class PriceListTab extends JPanel implements Refreshable {
        private final ListPanel<BangGia> lists;
        private final DefaultTableModel grid = new DefaultTableModel(new Object[]{"Món", "Size", "Giá bán (VNĐ)"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 2 && editable;
            }
        };
        private final JTable gridTable = new JTable(grid);
        private final JLabel gridTitle = Theme.muted("Chọn một bảng giá để xem / sửa giá.");
        private final JButton savePrices = Theme.primary("Lưu giá");
        private final List<int[]> rowKeys = new ArrayList<>();
        private BangGia current;
        private boolean editable;

        PriceListTab() {
            super(new BorderLayout());
            LocalDate today = LocalDate.now();
            lists = new ListPanel<>(services.catalog::findBangGia, new DataTable<>(
                    Column.text("Mô tả", 170, BangGia::moTa),
                    Column.date("Từ ngày", 85, BangGia::ngayBatDau),
                    Column.text("Đến ngày", 85, b -> b.ngayKetThuc() == null ? "Không thời hạn" : cafe.util.Dates.format(b.ngayKetThuc())),
                    Column.text("Tình trạng", 95, b -> b.tinhTrang(today))));
            lists.action(Theme.primary("Thêm"), false, b -> editBangGia(null));
            lists.action(Theme.button("Sửa"), true, this::editBangGia);
            lists.action(Theme.danger("Xóa"), true, b -> {
                if (Dialogs.confirm(this, "Xóa bảng giá \"" + b.moTa() + "\"?")) {
                    UiTask.exec(this, () -> services.catalog.deleteBangGia(b.maBangGia()), lists::reload);
                }
            });
            lists.table.onSelect(this::loadGrid);

            gridTable.setRowHeight(24);
            gridTable.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
            JPanel right = new JPanel(new BorderLayout(0, 6));
            right.setBorder(Theme.pad(10, 10));
            right.add(gridTitle, BorderLayout.NORTH);
            JScrollPane gridScroll = new JScrollPane(gridTable);
            Theme.styleScrollPane(gridScroll);
            right.add(gridScroll, BorderLayout.CENTER);
            savePrices.addActionListener(e -> save());
            savePrices.setEnabled(false);
            right.add(Fields.row(savePrices, Theme.muted("Để trống ô giá nếu món không bán size đó.")), BorderLayout.SOUTH);

            JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, lists, right);
            split.setResizeWeight(0.45);
            split.setBorder(null);
            add(split, BorderLayout.CENTER);
        }

        @Override
        public void reload() {
            lists.reload();
        }

        private void editBangGia(BangGia b) {
            UiTask.load(this, services.catalog::findBangGia, all -> {
                BangGia running = all.stream().filter(x -> x.apDungVao(LocalDate.now())).findFirst().orElse(null);
                FormDialog f = new FormDialog(this, b == null ? "Thêm bảng giá" : "Sửa bảng giá");
                JTextField moTa = Fields.text(b == null ? "" : b.moTa());
                JTextField tu = Fields.date(b == null ? LocalDate.now().plusDays(1) : b.ngayBatDau());
                JTextField den = Fields.date(b == null ? null : b.ngayKetThuc());
                JCheckBox copy = Fields.check("Sao chép giá từ bảng giá đang áp dụng", running != null);
                f.addRow("Mô tả *", moTa).addRow("Từ ngày *", tu).addRow("Đến ngày", den);
                if (b == null) {
                    f.addRow("", copy);
                    f.addWide(Theme.muted("<html><div style='width:380px'>Bảng giá mới không thời hạn sẽ tự động kết thúc bảng giá "
                            + "đang áp dụng vào ngày hôm trước. Hóa đơn cũ luôn giữ giá tại thời điểm bán.</div></html>"));
                }
                if (f.open(() -> {
                    BangGia input = new BangGia(b == null ? null : b.maBangGia(), moTa.getText(), Fields.getDate(tu, "Từ ngày"),
                            Fields.getDate(den, "Đến ngày"));
                    Integer copyFrom = b == null && copy.isSelected() && running != null ? running.maBangGia() : null;
                    return () -> services.catalog.saveBangGia(input, copyFrom);
                })) {
                    lists.reload();
                }
            });
        }

        private void loadGrid(BangGia b) {
            current = b;
            grid.setRowCount(0);
            rowKeys.clear();
            savePrices.setEnabled(false);
            if (b == null) {
                gridTitle.setText("Chọn một bảng giá để xem / sửa giá.");
                return;
            }
            UiTask.load(this, () -> new Object[]{services.catalog.findMon(), services.catalog.findSizes(),
                    services.catalog.findChiTietBangGia(b.maBangGia())}, r -> {
                @SuppressWarnings("unchecked") List<Mon> mons = (List<Mon>) r[0];
                @SuppressWarnings("unchecked") List<Size> sizeList = (List<Size>) r[1];
                @SuppressWarnings("unchecked") List<ChiTietBangGia> prices = (List<ChiTietBangGia>) r[2];
                Map<String, BigDecimal> byKey = new HashMap<>();
                prices.forEach(p -> byKey.put(p.maMon() + "-" + p.maSize(), p.giaBan()));
                for (Mon m : mons) {
                    for (Size s : sizeList) {
                        BigDecimal price = byKey.get(m.maMon() + "-" + s.maSize());
                        if (!m.trangThai() && price == null) {
                            continue;
                        }
                        grid.addRow(new Object[]{m.tenMon() + (m.trangThai() ? "" : " (ngừng bán)"), s.tenSize(),
                                price == null ? "" : Money.plain(price)});
                        rowKeys.add(new int[]{m.maMon(), s.maSize()});
                    }
                }
                editable = !b.daHetHieuLuc(LocalDate.now());
                savePrices.setEnabled(editable);
                gridTitle.setText("Giá trong \"" + b.moTa() + "\"" + (editable ? "" : " - đã hết hiệu lực, chỉ xem"));
            });
        }

        private void save() {
            if (gridTable.isEditing()) {
                gridTable.getCellEditor().stopCellEditing();
            }
            List<CatalogService.PriceEntry> entries = new ArrayList<>();
            try {
                for (int i = 0; i < grid.getRowCount(); i++) {
                    BigDecimal price = Money.parse(String.valueOf(grid.getValueAt(i, 2)), "Giá của " + grid.getValueAt(i, 0) + " size " + grid.getValueAt(i, 1));
                    if (price != null) {
                        entries.add(new CatalogService.PriceEntry(rowKeys.get(i)[0], rowKeys.get(i)[1], price));
                    }
                }
            } catch (BusinessException e) {
                Dialogs.error(this, e);
                return;
            }
            int id = current.maBangGia();
            UiTask.exec(this, () -> services.catalog.savePrices(id, entries), () -> Dialogs.info(this, "Đã lưu " + entries.size() + " giá."));
        }
    }
}
