package cafe.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Stock receipt. Created and confirmed by InventoryService.taoPhieuNhap (which also runs capNhatTonKho). */
public record PhieuNhap(Integer maPN, LocalDateTime ngayNhap, int maNV, String tenNV, int maNCC, String tenNCC,
                        TrangThaiPhieu trangThai, String ghiChu, BigDecimal tongTien, String lyDoHuy,
                        List<ChiTietPhieuNhap> chiTiet) {
}
