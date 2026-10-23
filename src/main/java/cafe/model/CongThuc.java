package cafe.model;

import java.math.BigDecimal;

/** Recipe line: dinhLuong of an ingredient for one size-M serving of a product. */
public record CongThuc(Integer maCT, int maMon, String tenMon, int maNL, String tenNL, String donViTinh, BigDecimal dinhLuong) {
}
