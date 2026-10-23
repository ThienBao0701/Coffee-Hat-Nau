package cafe.model;

import java.math.BigDecimal;
import java.util.List;

/** An invoice with its lines and (if paid) its payment. */
public record InvoiceDetail(HoaDon hoaDon, List<ChiTietHoaDon> chiTiet, ThanhToan thanhToan) {

    /** Live subtotal from the lines (used while the invoice is still OPEN). */
    public BigDecimal tamTinh() {
        return chiTiet.stream().map(ChiTietHoaDon::tinhThanhTien).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
