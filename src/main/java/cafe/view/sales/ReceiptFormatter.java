package cafe.view.sales;

import cafe.model.ChiTietHoaDon;
import cafe.model.ChiTietTopping;
import cafe.model.HoaDon;
import cafe.model.InvoiceDetail;
import cafe.model.ThanhToan;
import cafe.model.TrangThaiHoaDon;
import cafe.util.Dates;
import cafe.util.Money;

import java.math.BigDecimal;

/** Plain-text 42-column receipt (fits 80 mm thermal paper with a monospaced font). */
public final class ReceiptFormatter {

    static final int WIDTH = 42;

    private ReceiptFormatter() {
    }

    public static String format(InvoiceDetail d, String shopName, String address, String phone) {
        HoaDon h = d.hoaDon();
        boolean provisional = h.trangThai() == TrangThaiHoaDon.OPEN;
        StringBuilder sb = new StringBuilder();
        center(sb, shopName.toUpperCase());
        if (!address.isBlank()) {
            center(sb, address);
        }
        if (!phone.isBlank()) {
            center(sb, "ĐT: " + phone);
        }
        line(sb);
        center(sb, provisional ? "PHIẾU TẠM TÍNH" : "HÓA ĐƠN THANH TOÁN");
        if (h.trangThai() == TrangThaiHoaDon.REFUNDED) {
            center(sb, "*** ĐÃ HOÀN TIỀN ***");
        } else if (h.trangThai() == TrangThaiHoaDon.CANCELLED) {
            center(sb, "*** ĐÃ HỦY ***");
        }
        pair(sb, "Số HĐ: " + h.maHD(), Dates.format(h.ngayLap()));
        pair(sb, h.maBan() != null ? h.tenBan() : h.loaiDon().toString(), "NV: " + h.tenNV());
        if (h.diaChiGiao() != null) {
            sb.append(cut("Giao đến: " + h.diaChiGiao())).append('\n');
        }
        if (h.tenKH() != null) {
            sb.append(cut("Khách hàng: " + h.tenKH())).append('\n');
        }
        line(sb);
        sb.append(String.format("%-20s%3s%9s%10s\n", "Món", "SL", "Đ.giá", "T.tiền"));
        for (ChiTietHoaDon l : d.chiTiet()) {
            String name = cut(l.tenMon() + " (" + l.tenSize() + ")", 20);
            sb.append(String.format("%-20s%3d%9s%10s\n", name, l.soLuong(), Money.plain(l.donGia()),
                    Money.plain(l.donGia().multiply(BigDecimal.valueOf(l.soLuong())))));
            for (ChiTietTopping t : l.toppings()) {
                BigDecimal total = t.giaMotLy().multiply(BigDecimal.valueOf(l.soLuong()));
                sb.append(String.format("  + %-28s%10s\n", cut(t.toString(), 28), Money.plain(total)));
            }
            if (l.ghiChu() != null) {
                sb.append(cut("    * " + l.ghiChu())).append('\n');
            }
        }
        line(sb);
        if (provisional) {
            pair(sb, "Tạm tính:", Money.format(d.tamTinh()));
            sb.append("(Khuyến mãi và thuế tính khi thanh toán)\n");
        } else {
            pair(sb, "Tạm tính:", Money.format(h.tamTinh()));
            if (h.tienGiam().signum() > 0) {
                pair(sb, cut("Giảm giá (" + h.tenKM() + "):", 28), "-" + Money.format(h.tienGiam()));
            }
            pair(sb, "Thuế (" + h.thueSuat().stripTrailingZeros().toPlainString() + "%):", Money.format(h.tienThue()));
            pair(sb, "TỔNG CỘNG:", Money.format(h.tongTien()));
            ThanhToan t = d.thanhToan();
            if (t != null) {
                pair(sb, t.phuongThuc() + ":", Money.format(t.tienKhachDua()));
                if (t.tienThoi().signum() > 0) {
                    pair(sb, "Tiền thối:", Money.format(t.tienThoi()));
                }
                pair(sb, "Thanh toán lúc:", Dates.format(t.thoiDiem()));
            }
        }
        line(sb);
        center(sb, "Cảm ơn quý khách, hẹn gặp lại!");
        return sb.toString();
    }

    private static void line(StringBuilder sb) {
        sb.append("-".repeat(WIDTH)).append('\n');
    }

    private static void center(StringBuilder sb, String s) {
        String t = cut(s);
        sb.append(" ".repeat((WIDTH - t.length()) / 2)).append(t).append('\n');
    }

    private static void pair(StringBuilder sb, String left, String right) {
        int space = Math.max(1, WIDTH - left.length() - right.length());
        sb.append(left).append(" ".repeat(space)).append(right).append('\n');
    }

    private static String cut(String s) {
        return cut(s, WIDTH);
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
