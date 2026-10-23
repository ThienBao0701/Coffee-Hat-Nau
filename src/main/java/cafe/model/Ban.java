package cafe.model;

/** trangThai = table in service (true) / out of service (false). */
public record Ban(Integer maBan, String tenBan, int maKhuVuc, String tenKhuVuc, int soCho, boolean trangThai) {
    @Override
    public String toString() {
        return tenBan + " (" + tenKhuVuc + ")";
    }
}
