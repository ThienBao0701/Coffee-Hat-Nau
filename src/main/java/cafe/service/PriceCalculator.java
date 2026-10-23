package cafe.service;

import cafe.model.KhuyenMai;
import cafe.util.BusinessException;
import cafe.util.Money;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The single place where invoice totals are computed (UI preview and payment use the same code).
 * Order of operations:
 *   1. subtotal  = sum of lines (unit price x qty + toppings)
 *   2. discount  = percent of subtotal (capped by giamToiDa) or fixed amount; never more than the subtotal
 *   3. tax       = tax rate x (subtotal - discount)
 *   4. total     = subtotal - discount + tax
 * Discount and tax are rounded to whole dong (HALF_UP) - see Money.
 */
public final class PriceCalculator {

    private PriceCalculator() {
    }

    public record Totals(BigDecimal tamTinh, BigDecimal tienGiam, BigDecimal tienThue, BigDecimal tongTien) {
    }

    /** Throws if the promotion cannot be used for this subtotal on this date. */
    public static void checkEligible(KhuyenMai km, BigDecimal tamTinh, LocalDate date) {
        if (km == null) {
            return;
        }
        if (!km.trangThai()) {
            throw new BusinessException("Khuyến mãi \"" + km.tenKM() + "\" đang tạm ngừng.");
        }
        if (date.isBefore(km.ngayBatDau()) || date.isAfter(km.ngayKetThuc())) {
            throw new BusinessException("Khuyến mãi \"" + km.tenKM() + "\" không còn hiệu lực hoặc chưa bắt đầu.");
        }
        if (tamTinh.compareTo(km.giaTriDonToiThieu()) < 0) {
            throw new BusinessException("Đơn hàng chưa đạt giá trị tối thiểu " + Money.format(km.giaTriDonToiThieu())
                    + " để áp dụng khuyến mãi.");
        }
    }

    public static BigDecimal discount(BigDecimal tamTinh, KhuyenMai km) {
        if (km == null || tamTinh.signum() <= 0) {
            return Money.ZERO;
        }
        BigDecimal d = km.phanTramGiam() != null ? Money.percentOf(tamTinh, km.phanTramGiam()) : Money.round(km.phanTienGiam());
        if (km.giamToiDa() != null) {
            d = d.min(km.giamToiDa());
        }
        return Money.round(d.min(tamTinh));
    }

    public static Totals calculate(BigDecimal tamTinh, KhuyenMai km, BigDecimal taxPercent) {
        BigDecimal subtotal = Money.round(tamTinh);
        BigDecimal giam = discount(subtotal, km);
        BigDecimal taxable = subtotal.subtract(giam);
        BigDecimal thue = taxPercent == null ? Money.ZERO : Money.percentOf(taxable, taxPercent);
        return new Totals(subtotal, giam, thue, taxable.add(thue));
    }
}
