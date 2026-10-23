package cafe.model;

public enum TrangThaiDatBan {
    CHO_XAC_NHAN("Chờ xác nhận"),
    DA_XAC_NHAN("Đã xác nhận"),
    DA_HUY("Đã hủy"),
    HOAN_THANH("Hoàn thành");

    private final String label;

    TrangThaiDatBan(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
