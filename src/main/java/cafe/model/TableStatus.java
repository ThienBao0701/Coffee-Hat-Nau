package cafe.model;

/**
 * Live status of a table, derived from data instead of stored, so it can never drift:
 * occupied = has an OPEN invoice, reserved = has an active reservation around now.
 */
public record TableStatus(Ban ban, TrangThaiBan trangThai, Integer maHoaDonMo, String ghiChu) {
}
