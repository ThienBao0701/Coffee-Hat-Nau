package cafe.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Report rows. Revenue definitions used everywhere (see README):
 *  - Doanh so (gross sales)   = sum of subtotals of invoices PAID in the period (incl. ones refunded later)
 *  - Giam gia (discounts)     = sum of discounts of those invoices
 *  - Thue (tax)               = sum of tax of those invoices (collected for the state, not revenue)
 *  - Hoan tien (refunds)      = pre-tax value (subtotal - discount) of invoices REFUNDED in the period
 *  - Doanh thu thuan (net)    = gross sales - discounts - refunds
 *  - Tong thu (collected)     = sum of amounts paid (incl. tax) of invoices paid in the period
 */
public final class Reports {

    private Reports() {
    }

    public record RevenueSummary(int soHoaDon, BigDecimal doanhSo, BigDecimal giamGia, BigDecimal thue,
                                 int soHoaDonHoan, BigDecimal hoanTien, BigDecimal tongThu, int soHoaDonHuy) {

        public RevenueSummary(int soHoaDon, BigDecimal doanhSo, BigDecimal giamGia, BigDecimal thue,
                              int soHoaDonHoan, BigDecimal hoanTien, BigDecimal tongThu) {
            this(soHoaDon, doanhSo, giamGia, thue, soHoaDonHoan, hoanTien, tongThu, 0);
        }

        public BigDecimal doanhThuThuan() {
            return doanhSo.subtract(giamGia).subtract(hoanTien);
        }

        /** Average paid invoice value (incl. tax). */
        public BigDecimal trungBinhHoaDon() {
            return soHoaDon == 0 ? BigDecimal.ZERO : tongThu.divide(BigDecimal.valueOf(soHoaDon), 0, RoundingMode.HALF_UP);
        }
    }

    /** One day or month of sales. */
    public record RevenueRow(String ky, int soHoaDon, BigDecimal doanhSo, BigDecimal giamGia, BigDecimal thue, BigDecimal tongThu) {
        public BigDecimal sauGiam() {
            return doanhSo.subtract(giamGia);
        }
    }

    public record ProductSales(String tenMon, int soLuong, BigDecimal doanhSo) {
    }

    public record EmployeeSales(int maNV, String tenNV, int soHoaDon, BigDecimal sauGiam, BigDecimal tongThu) {
        public EmployeeSales(String tenNV, int soHoaDon, BigDecimal sauGiam, BigDecimal tongThu) {
            this(0, tenNV, soHoaDon, sauGiam, tongThu);
        }
    }

    public record PaymentMethodTotal(String phuongThuc, int soGiaoDich, BigDecimal tongThu) {
    }

    public record CashierSales(int maNV, String tenNV, int soHoaDon, BigDecimal tongThu) {
    }

    public record TransactionRow(int maHD, String thoiDiem, String trangThai, int maNhanVien, String nhanVien,
                                 int maThuNgan, String thuNgan, String phuongThuc, BigDecimal tongTien) {
    }

    public record StockRow(String tenNL, String donViTinh, BigDecimal tonDau, BigDecimal nhap, BigDecimal xuat, BigDecimal tonCuoi) {
    }
}
