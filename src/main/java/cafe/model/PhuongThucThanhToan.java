package cafe.model;

public enum PhuongThucThanhToan {
    TIEN_MAT("Tiền mặt"),
    CHUYEN_KHOAN("Chuyển khoản"),
    THE("Thẻ");

    private final String label;

    PhuongThucThanhToan(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
