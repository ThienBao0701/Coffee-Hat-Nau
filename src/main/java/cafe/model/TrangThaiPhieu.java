package cafe.model;

public enum TrangThaiPhieu {
    DA_XAC_NHAN("Đã xác nhận"),
    DA_HUY("Đã hủy");

    private final String label;

    TrangThaiPhieu(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
