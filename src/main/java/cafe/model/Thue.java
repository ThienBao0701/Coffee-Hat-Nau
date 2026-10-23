package cafe.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record Thue(Integer maThue, String tenThue, BigDecimal phanTram, LocalDate ngayBatDau, LocalDate ngayKetThuc) {

    public boolean apDungVao(LocalDate d) {
        return !ngayBatDau.isAfter(d) && (ngayKetThuc == null || !ngayKetThuc.isBefore(d));
    }

    @Override
    public String toString() {
        return tenThue;
    }
}
