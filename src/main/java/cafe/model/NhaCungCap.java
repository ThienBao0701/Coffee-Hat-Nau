package cafe.model;

public record NhaCungCap(Integer maNCC, String tenNCC, String soDienThoai, String diaChi, String email,
                         String ghiChu, boolean trangThai) {
    @Override
    public String toString() {
        return tenNCC;
    }
}
