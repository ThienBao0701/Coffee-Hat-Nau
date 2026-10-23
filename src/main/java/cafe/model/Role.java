package cafe.model;

public enum Role {
    CHU_CUA_HANG("Chủ cửa hàng"),
    QUAN_LY("Quản lý"),
    NHAN_VIEN("Nhân viên");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
