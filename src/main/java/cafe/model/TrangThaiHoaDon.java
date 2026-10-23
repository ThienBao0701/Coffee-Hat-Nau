package cafe.model;

public enum TrangThaiHoaDon {
    OPEN("Đang phục vụ"),
    PAID("Đã thanh toán"),
    CANCELLED("Đã hủy"),
    REFUNDED("Đã hoàn tiền");

    private final String label;

    TrangThaiHoaDon(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
