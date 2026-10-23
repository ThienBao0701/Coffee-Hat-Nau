package cafe.model;

import java.math.BigDecimal;

/** Topping on an invoice line. soLuong = toppings per ONE drink; giaThem is the price snapshot. */
public record ChiTietTopping(int maTopping, String tenTopping, int soLuong, BigDecimal giaThem) {

    public BigDecimal giaMotLy() {
        return giaThem.multiply(BigDecimal.valueOf(soLuong));
    }

    @Override
    public String toString() {
        return soLuong > 1 ? tenTopping + " x" + soLuong : tenTopping;
    }
}
