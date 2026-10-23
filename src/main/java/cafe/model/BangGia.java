package cafe.model;

import java.time.LocalDate;

public record BangGia(Integer maBangGia, String moTa, LocalDate ngayBatDau, LocalDate ngayKetThuc) {

    public boolean apDungVao(LocalDate d) {
        return !ngayBatDau.isAfter(d) && (ngayKetThuc == null || !ngayKetThuc.isBefore(d));
    }

    /** Ended before today: kept read-only as price history. */
    public boolean daHetHieuLuc(LocalDate today) {
        return ngayKetThuc != null && ngayKetThuc.isBefore(today);
    }

    public String tinhTrang(LocalDate today) {
        if (apDungVao(today)) {
            return "Đang áp dụng";
        }
        return ngayBatDau.isAfter(today) ? "Sắp áp dụng" : "Hết hiệu lực";
    }

    @Override
    public String toString() {
        return moTa;
    }
}
