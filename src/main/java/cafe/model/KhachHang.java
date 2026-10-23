package cafe.model;

import java.time.LocalDate;

public record KhachHang(Integer maKH, String tenKH, LocalDate ngaySinh, String soCMND, String soDienThoai, String diaChi) {
    @Override
    public String toString() {
        return soDienThoai == null ? tenKH : tenKH + " - " + soDienThoai;
    }
}
