package cafe.view.admin;

import cafe.model.NhatKy;
import cafe.service.Services;
import cafe.util.Check;
import cafe.view.common.Column;
import cafe.view.common.DataTable;
import cafe.view.common.Fields;
import cafe.view.common.ListPanel;
import cafe.view.common.Refreshable;
import cafe.view.common.Theme;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.time.LocalDate;

/** Read-only audit trail: who did what, when (manager). */
public class AuditPanel extends JPanel implements Refreshable {

    private final JTextField from = Fields.date(LocalDate.now().minusDays(7));
    private final JTextField to = Fields.date(LocalDate.now());
    private final ListPanel<NhatKy> list;

    public AuditPanel(Services services) {
        super(new BorderLayout());
        list = new ListPanel<>(() -> services.audit.find(Check.notNull(Fields.getDate(from, "Từ ngày"), "từ ngày"),
                Check.notNull(Fields.getDate(to, "Đến ngày"), "đến ngày")), new DataTable<>(
                Column.dateTime("Thời điểm", 130, NhatKy::thoiDiem),
                Column.text("Người thực hiện", 140, n -> n.tenNV() == null ? "(chưa đăng nhập)" : n.tenNV()),
                Column.text("Hành động", 150, NhatKy::hanhDong),
                Column.text("Đối tượng", 120, NhatKy::doiTuong),
                Column.text("Mã", 60, n -> n.maDoiTuong() == null ? "" : n.maDoiTuong()),
                Column.text("Chi tiết", 360, n -> n.chiTiet() == null ? "" : n.chiTiet())));
        JButton filter = Theme.button("Lọc");
        filter.addActionListener(e -> list.reload());
        from.setColumns(8);
        to.setColumns(8);
        list.addFilter(Fields.row(new JLabel("Từ"), from, new JLabel("đến"), to, filter));
        add(Theme.screen("Nhật ký hệ thống", list), BorderLayout.CENTER);
    }

    @Override
    public void reload() {
        list.reload();
    }
}
