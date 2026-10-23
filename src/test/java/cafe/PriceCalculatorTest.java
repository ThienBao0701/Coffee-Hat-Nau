package cafe;

import cafe.model.KhuyenMai;
import cafe.service.PriceCalculator;
import cafe.util.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PriceCalculatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 1);

    private static KhuyenMai percent(String pct, String max, String min) {
        return new KhuyenMai(1, "KM %", 1, "", new BigDecimal(pct), null, TODAY.minusDays(1), TODAY.plusDays(1),
                new BigDecimal(min), max == null ? null : new BigDecimal(max), true);
    }

    private static KhuyenMai fixed(String amount) {
        return new KhuyenMai(2, "KM tiền", 2, "", null, new BigDecimal(amount), TODAY.minusDays(1), TODAY.plusDays(1),
                BigDecimal.ZERO, null, true);
    }

    private static BigDecimal n(String s) {
        return new BigDecimal(s);
    }

    @Test
    void percentDiscountThenTaxOnDiscountedAmount() {
        PriceCalculator.Totals t = PriceCalculator.calculate(n("111000"), percent("10", "50000", "0"), n("8"));
        assertEquals(0, t.tienGiam().compareTo(n("11100")));
        assertEquals(0, t.tienThue().compareTo(n("7992")));      // 8% of 99,900
        assertEquals(0, t.tongTien().compareTo(n("107892")));
    }

    @Test
    void percentDiscountIsCappedByMaximum() {
        PriceCalculator.Totals t = PriceCalculator.calculate(n("1000000"), percent("10", "50000", "0"), null);
        assertEquals(0, t.tienGiam().compareTo(n("50000")));
        assertEquals(0, t.tongTien().compareTo(n("950000")));
    }

    @Test
    void fixedDiscountNeverExceedsSubtotal() {
        PriceCalculator.Totals t = PriceCalculator.calculate(n("15000"), fixed("20000"), n("8"));
        assertEquals(0, t.tienGiam().compareTo(n("15000")));
        assertEquals(0, t.tienThue().signum());
        assertEquals(0, t.tongTien().signum());
    }

    @Test
    void amountsAreRoundedToWholeDongHalfUp() {
        // 7% of 12,345 = 864.15 -> 864 ; tax 8% of 11,481 = 918.48 -> 918
        PriceCalculator.Totals t = PriceCalculator.calculate(n("12345"), percent("7", null, "0"), n("8"));
        assertEquals(0, t.tienGiam().compareTo(n("864")));
        assertEquals(0, t.tienThue().compareTo(n("918")));
        assertEquals(0, t.tongTien().compareTo(n("12399")));
    }

    @Test
    void noPromotionNoTax() {
        PriceCalculator.Totals t = PriceCalculator.calculate(n("50000"), null, null);
        assertEquals(0, t.tongTien().compareTo(n("50000")));
    }

    @Test
    void eligibilityRules() {
        KhuyenMai minOrder = percent("10", null, "100000");
        assertThrows(BusinessException.class, () -> PriceCalculator.checkEligible(minOrder, n("99999"), TODAY));
        PriceCalculator.checkEligible(minOrder, n("100000"), TODAY);

        assertThrows(BusinessException.class, () -> PriceCalculator.checkEligible(minOrder, n("200000"), TODAY.plusDays(5)));
        KhuyenMai inactive = new KhuyenMai(3, "Tắt", 1, "", n("10"), null, TODAY, TODAY, BigDecimal.ZERO, null, false);
        assertThrows(BusinessException.class, () -> PriceCalculator.checkEligible(inactive, n("200000"), TODAY));
    }
}
