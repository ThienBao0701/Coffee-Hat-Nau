package cafe.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/** Invoice line: one product + size with its own toppings and note. donGia is the price snapshot. */
public record ChiTietHoaDon(Integer maCTHD, int maHD, int maMon, int maSize, String tenMon, String tenSize,
                            int soLuong, BigDecimal donGia, String ghiChu, List<ChiTietTopping> toppings) {

    /** Topping cost for the whole line = sum(topping price x toppings per drink) x quantity. */
    public BigDecimal tinhTienTopping() {
        BigDecimal perDrink = toppings.stream().map(ChiTietTopping::giaMotLy).reduce(BigDecimal.ZERO, BigDecimal::add);
        return perDrink.multiply(BigDecimal.valueOf(soLuong));
    }

    public BigDecimal tinhThanhTien() {
        return donGia.multiply(BigDecimal.valueOf(soLuong)).add(tinhTienTopping());
    }

    public String moTaTopping() {
        return toppings.stream().map(ChiTietTopping::toString).collect(Collectors.joining(", "));
    }
}
