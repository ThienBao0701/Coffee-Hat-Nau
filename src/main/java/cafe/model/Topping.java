package cafe.model;

import java.math.BigDecimal;

public record Topping(Integer maTopping, String tenTopping, BigDecimal giaThem, boolean trangThai) {
    @Override
    public String toString() {
        return tenTopping;
    }
}
