package cafe.model;

public enum LoaiBienDong {
    TON_DAU("Tồn đầu"),
    NHAP("Nhập kho"),
    XUAT("Xuất kho"),
    BAN_HANG("Bán hàng"),
    HUY_NHAP("Hủy phiếu nhập"),
    HUY_XUAT("Hủy phiếu xuất"),
    DIEU_CHINH("Điều chỉnh kiểm kê");

    private final String label;

    LoaiBienDong(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
