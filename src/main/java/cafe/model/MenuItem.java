package cafe.model;

import java.math.BigDecimal;
import java.util.List;

/** An active product with its current selling price per size (from the price list in effect). */
public record MenuItem(Mon mon, List<SizePrice> gia) {

    public record SizePrice(Size size, BigDecimal giaBan) {
        @Override
        public String toString() {
            return size.tenSize();
        }
    }
}
