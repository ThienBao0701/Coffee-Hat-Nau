package cafe.model;

import java.math.BigDecimal;

/** heSoDinhLuong: recipe quantity multiplier for this size (M = 1.00). */
public record Size(Integer maSize, String tenSize, BigDecimal heSoDinhLuong, int thuTu) {
    @Override
    public String toString() {
        return tenSize;
    }
}
