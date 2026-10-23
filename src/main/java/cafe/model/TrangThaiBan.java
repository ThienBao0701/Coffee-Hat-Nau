package cafe.model;

public enum TrangThaiBan {
    TRONG("Trống"),
    CO_KHACH("Có khách"),
    DA_DAT("Đã đặt"),
    NGUNG("Ngừng phục vụ");

    private final String label;

    TrangThaiBan(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
