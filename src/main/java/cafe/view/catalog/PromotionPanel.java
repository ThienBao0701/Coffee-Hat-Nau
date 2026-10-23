package cafe.view.catalog;

import cafe.model.KhuyenMai;
import cafe.model.LoaiKhuyenMai;
import cafe.model.Thue;
import cafe.service.Services;
import cafe.util.BusinessException;
import cafe.util.Dates;
import cafe.view.common.Column;
import cafe.view.common.DataTable;
import cafe.view.common.Dialogs;
import cafe.view.common.Fields;
import cafe.view.common.FormDialog;
import cafe.view.common.ListPanel;
import cafe.view.common.Refreshable;
import cafe.view.common.Theme;
import cafe.view.common.UiTask;

import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Promotion types, promotions and tax rates (manager). */
public class PromotionPanel extends JPanel implements Refreshable {

    private final Services services;
    private final JTabbedPane tabs = new JTabbedPane();
    private final ListPanel<KhuyenMai> promotions;
    private final ListPanel<LoaiKhuyenMai> types;
    private final ListPanel<Thue> taxes;

    public PromotionPanel(Services services) {
        super(new BorderLayout());
        this.services = services;
        LocalDate today = LocalDate.now();

        promotions = new ListPanel<>(services.promotions::findKhuyenMai, new DataTable<>(
                Column.text("Tên khuyến mãi", 220, KhuyenMai::tenKM),
                Column.text("Mức giảm", 140, k -> new LoaiKhuyenMai(k.maLoaiKM(), k.tenLoaiKM(), k.phanTramGiam(), k.phanTienGiam()).moTaGiam()),
                Column.date("Từ ngày", 90, KhuyenMai::ngayBatDau),
                Column.date("Đến ngày", 90, KhuyenMai::ngayKetThuc),
                Column.money("Đơn tối thiểu", 110, KhuyenMai::giaTriDonToiThieu),
                Column.money("Giảm tối đa", 100, KhuyenMai::giamToiDa),
                Column.text("Tình trạng", 110, k -> !k.trangThai() ? "Tạm ngừng" : k.hieuLucVao(today) ? "Đang áp dụng"
                        : k.ngayKetThuc().isBefore(today) ? "Hết hạn" : "Chưa bắt đầu")));
        promotions.action(Theme.primary("Thêm khuyến mãi"), false, k -> editPromotion(null));
        promotions.action(Theme.button("Sửa"), true, this::editPromotion);
        promotions.table.onDoubleClick(this::editPromotion);

        types = new ListPanel<>(services.promotions::findLoai, new DataTable<>(
                Column.text("Loại khuyến mãi", 220, LoaiKhuyenMai::tenLoaiKM),
                Column.text("Kiểu giảm", 120, l -> l.phanTramGiam() != null ? "Theo %" : "Số tiền cố định"),
                Column.text("Mức giảm", 120, LoaiKhuyenMai::moTaGiam)));
        types.action(Theme.primary("Thêm loại"), false, l -> editType(null));
        types.action(Theme.button("Sửa"), true, this::editType);
        types.action(Theme.danger("Xóa"), true, l -> {
            if (Dialogs.confirm(this, "Xóa loại khuyến mãi \"" + l.tenLoaiKM() + "\"?")) {
                UiTask.exec(this, () -> services.promotions.deleteLoai(l.maLoaiKM()), types::reload);
            }
        });

        taxes = new ListPanel<>(services.promotions::findThue, new DataTable<>(
                Column.text("Tên thuế", 180, Thue::tenThue),
                Column.qty("Thuế suất (%)", 100, Thue::phanTram),
                Column.date("Từ ngày", 90, Thue::ngayBatDau),
                Column.text("Đến ngày", 110, t -> t.ngayKetThuc() == null ? "Không thời hạn" : Dates.format(t.ngayKetThuc())),
                Column.text("Tình trạng", 110, t -> t.apDungVao(today) ? "Đang áp dụng" : "")));
        taxes.action(Theme.primary("Thêm mức thuế"), false, t -> editTax(null));
        taxes.action(Theme.button("Sửa"), true, this::editTax);

        tabs.addTab("Khuyến mãi", promotions);
        tabs.addTab("Loại khuyến mãi", types);
        tabs.addTab("Thuế", taxes);
        tabs.addChangeListener(e -> reload());
        add(Theme.screen("Khuyến mãi & thuế", tabs), BorderLayout.CENTER);
    }

    @Override
    public void reload() {
        ((Refreshable) tabs.getSelectedComponent()).reload();
    }

    private void editPromotion(KhuyenMai k) {
        UiTask.load(this, services.promotions::findLoai, list -> {
            if (list.isEmpty()) {
                Dialogs.info(this, "Hãy tạo loại khuyến mãi trước.");
                return;
            }
            FormDialog f = new FormDialog(this, k == null ? "Thêm khuyến mãi" : "Sửa khuyến mãi");
            JTextField ten = Fields.text(k == null ? "" : k.tenKM());
            JComboBox<LoaiKhuyenMai> loai = Fields.combo(list, k == null ? null
                    : list.stream().filter(l -> l.maLoaiKM() == k.maLoaiKM()).findFirst().orElse(null));
            JTextField tu = Fields.date(k == null ? LocalDate.now() : k.ngayBatDau());
            JTextField den = Fields.date(k == null ? LocalDate.now().plusMonths(1) : k.ngayKetThuc());
            JTextField min = Fields.money(k == null ? BigDecimal.ZERO : k.giaTriDonToiThieu());
            JTextField max = Fields.money(k == null ? null : k.giamToiDa());
            JCheckBox active = Fields.check("Đang hoạt động", k == null || k.trangThai());
            f.addRow("Tên khuyến mãi *", ten).addRow("Loại / mức giảm *", loai).addRow("Từ ngày *", tu).addRow("Đến ngày *", den)
                    .addRow("Giá trị đơn tối thiểu", min).addRow("Giảm tối đa (để trống = không giới hạn)", max).addRow("", active);
            if (f.open(() -> {
                LoaiKhuyenMai l = (LoaiKhuyenMai) loai.getSelectedItem();
                KhuyenMai input = new KhuyenMai(k == null ? null : k.maKM(), ten.getText(), l.maLoaiKM(), null, null, null,
                        Fields.getDate(tu, "Từ ngày"), Fields.getDate(den, "Đến ngày"), Fields.getMoney(min, "Giá trị đơn tối thiểu"),
                        Fields.getMoney(max, "Giảm tối đa"), active.isSelected());
                return () -> services.promotions.saveKhuyenMai(input);
            })) {
                promotions.reload();
            }
        });
    }

    private void editType(LoaiKhuyenMai l) {
        FormDialog f = new FormDialog(this, l == null ? "Thêm loại khuyến mãi" : "Sửa loại khuyến mãi");
        JTextField ten = Fields.text(l == null ? "" : l.tenLoaiKM());
        JRadioButton percent = new JRadioButton("Giảm theo %", l == null || l.phanTramGiam() != null);
        JRadioButton amount = new JRadioButton("Giảm số tiền cố định", l != null && l.phanTienGiam() != null);
        ButtonGroup g = new ButtonGroup();
        g.add(percent);
        g.add(amount);
        BigDecimal currentValue = l == null ? null : (l.phanTramGiam() != null ? l.phanTramGiam() : l.phanTienGiam());
        JTextField value = Fields.decimal(currentValue);
        f.addRow("Tên loại *", ten).addRow("Kiểu giảm *", Fields.row(percent, amount)).addRow("Mức giảm (% hoặc VNĐ) *", value);
        if (f.open(() -> {
            BigDecimal v = Fields.getDecimal(value, "Mức giảm");
            if (v == null) {
                throw new BusinessException("Vui lòng nhập mức giảm.");
            }
            LoaiKhuyenMai input = new LoaiKhuyenMai(l == null ? null : l.maLoaiKM(), ten.getText(),
                    percent.isSelected() ? v : null, percent.isSelected() ? null : v);
            return () -> services.promotions.saveLoai(input);
        })) {
            types.reload();
        }
    }

    private void editTax(Thue t) {
        FormDialog f = new FormDialog(this, t == null ? "Thêm mức thuế" : "Sửa mức thuế");
        JTextField ten = Fields.text(t == null ? "" : t.tenThue());
        JTextField pct = Fields.decimal(t == null ? null : t.phanTram());
        JTextField tu = Fields.date(t == null ? LocalDate.now() : t.ngayBatDau());
        JTextField den = Fields.date(t == null ? null : t.ngayKetThuc());
        f.addRow("Tên thuế *", ten).addRow("Thuế suất (%) *", pct).addRow("Từ ngày *", tu).addRow("Đến ngày", den);
        f.addWide(Theme.muted("<html><div style='width:360px'>Các mức thuế không được trùng thời gian. Để đổi thuế suất, hãy đặt "
                + "ngày kết thúc cho mức cũ rồi thêm mức mới. Hóa đơn đã lập giữ nguyên thuế suất cũ.</div></html>"));
        if (f.open(() -> {
            Thue input = new Thue(t == null ? null : t.maThue(), ten.getText(), Fields.getDecimal(pct, "Thuế suất"),
                    Fields.getDate(tu, "Từ ngày"), Fields.getDate(den, "Đến ngày"));
            return () -> services.promotions.saveThue(input);
        })) {
            taxes.reload();
        }
    }
}
