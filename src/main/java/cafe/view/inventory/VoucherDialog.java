package cafe.view.inventory;

import cafe.model.ChiTietPhieuNhap;
import cafe.model.ChiTietPhieuXuat;
import cafe.model.NguyenLieu;
import cafe.model.NhaCungCap;
import cafe.service.Services;
import cafe.util.BusinessException;
import cafe.util.Check;
import cafe.util.Money;
import cafe.view.common.Column;
import cafe.view.common.DataTable;
import cafe.view.common.Dialogs;
import cafe.view.common.Fields;
import cafe.view.common.FormDialog;
import cafe.view.common.Theme;
import cafe.view.common.UiTask;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Creates a stock receipt (phiếu nhập) or a stock issue (phiếu xuất) with several ingredient lines. */
public final class VoucherDialog {

    private record Line(NguyenLieu nl, BigDecimal soLuong, BigDecimal donGia) {
        BigDecimal thanhTien() {
            return soLuong.multiply(donGia);
        }
    }

    private VoucherDialog() {
    }

    /** @param suppliers null for an issue voucher */
    public static boolean show(Component owner, Services services, List<NguyenLieu> ingredients, List<NhaCungCap> suppliers) {
        boolean receipt = suppliers != null;
        FormDialog f = new FormDialog(owner, receipt ? "Tạo phiếu nhập kho" : "Tạo phiếu xuất kho");
        JComboBox<NhaCungCap> supplier = receipt ? Fields.combo(suppliers, null) : null;
        JTextField note = Fields.text("");
        if (receipt) {
            f.addRow("Nhà cung cấp *", supplier).addRow("Ghi chú", note);
        } else {
            f.addRow("Lý do xuất *", note);
            note.setToolTipText("Ví dụ: hư hỏng, hết hạn, dùng nội bộ");
        }

        List<Line> lines = new ArrayList<>();
        DataTable<Line> table = new DataTable<>(
                Column.text("Nguyên liệu", 170, l -> l.nl().tenNL()),
                Column.text("ĐVT", 60, l -> l.nl().donViTinh()),
                Column.qty("Số lượng", 80, Line::soLuong),
                Column.money(receipt ? "Giá nhập" : "Đơn giá", 90, Line::donGia),
                Column.money("Thành tiền", 100, Line::thanhTien));
        table.setEmptyText("Chưa có dòng nào");
        table.setPreferredSize(new Dimension(560, 200));
        JLabel total = new JLabel("Tổng: 0 đ");
        total.setFont(Theme.BOLD);

        JComboBox<NguyenLieu> nl = Fields.combo(ingredients, null);
        JTextField qty = Fields.decimal(null);
        qty.setColumns(6);
        JTextField price = Fields.money(null);
        price.setColumns(8);
        nl.addActionListener(e -> {
            NguyenLieu n = (NguyenLieu) nl.getSelectedItem();
            if (n != null) {
                UiTask.load(nl, () -> services.inventory.findLastPurchasePrice(n.maNL()), p -> price.setText(Money.plain(p)));
            }
        });
        JButton add = Theme.button("Thêm dòng");
        add.addActionListener(e -> {
            try {
                NguyenLieu n = Check.notNull((NguyenLieu) nl.getSelectedItem(), "nguyên liệu");
                BigDecimal q = Check.positive(Fields.getDecimal(qty, "Số lượng"), "Số lượng");
                BigDecimal p = Check.nonNegative(Fields.getMoney(price, "Đơn giá"), "Đơn giá");
                if (lines.stream().anyMatch(l -> l.nl().maNL().equals(n.maNL()))) {
                    throw new BusinessException("Nguyên liệu này đã có trong phiếu.");
                }
                lines.add(new Line(n, q, p));
            } catch (BusinessException ex) {
                Dialogs.error(add, ex);
                return;
            }
            table.setRows(lines);
            total.setText("Tổng: " + Money.format(lines.stream().map(Line::thanhTien).reduce(BigDecimal.ZERO, BigDecimal::add)));
            qty.setText("");
        });
        JButton remove = Theme.danger("Xóa dòng");
        remove.addActionListener(e -> {
            Line l = table.getSelected();
            if (l != null) {
                lines.remove(l);
                table.setRows(lines);
                total.setText("Tổng: " + Money.format(lines.stream().map(Line::thanhTien).reduce(BigDecimal.ZERO, BigDecimal::add)));
            }
        });
        JPanel entry = Fields.row(new JLabel("Nguyên liệu"), nl, new JLabel("SL"), qty, new JLabel(receipt ? "Giá" : "Đơn giá"), price, add);
        JPanel box = new JPanel(new BorderLayout(0, 6));
        box.add(entry, BorderLayout.NORTH);
        box.add(table, BorderLayout.CENTER);
        box.add(Fields.row(remove, total), BorderLayout.SOUTH);
        f.addWide(box);
        if (!ingredients.isEmpty()) {
            nl.setSelectedIndex(0);
        }
        f.setSaveText(receipt ? "Lưu & nhập kho" : "Lưu & xuất kho");
        return f.open(() -> {
            if (lines.isEmpty()) {
                throw new BusinessException("Phiếu phải có ít nhất một nguyên liệu.");
            }
            String n = note.getText();
            if (receipt) {
                NhaCungCap s = Check.notNull((NhaCungCap) supplier.getSelectedItem(), "nhà cung cấp");
                List<ChiTietPhieuNhap> data = lines.stream().map(l -> new ChiTietPhieuNhap(l.nl().maNL(), null, null, l.soLuong(), l.donGia())).toList();
                return () -> services.inventory.taoPhieuNhap(s.maNCC(), n, data);
            }
            List<ChiTietPhieuXuat> data = lines.stream().map(l -> new ChiTietPhieuXuat(l.nl().maNL(), null, null, l.soLuong(), l.donGia())).toList();
            return () -> services.inventory.taoPhieuXuat(n, data);
        });
    }
}
