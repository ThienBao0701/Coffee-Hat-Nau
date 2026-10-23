package cafe.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Stock issue voucher (spoilage, internal use...). Created by InventoryService.taoPhieuXuat. */
public record PhieuXuat(Integer maPX, LocalDateTime ngayXuat, int maNV, String tenNV, String lyDo,
                        TrangThaiPhieu trangThai, BigDecimal tongTien, String lyDoHuy, List<ChiTietPhieuXuat> chiTiet) {
}
