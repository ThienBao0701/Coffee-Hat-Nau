package cafe.model;

public enum LoaiDon {
    TAI_BAN("Tại bàn"),
    MANG_DI("Mang đi"),
    GIAO_HANG("Giao hàng");

    private final String label;

    LoaiDon(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
