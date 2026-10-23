package cafe.model;

public record Mon(Integer maMon, String tenMon, int maLoai, String tenLoai, boolean trangThai) {
    @Override
    public String toString() {
        return tenMon;
    }
}
