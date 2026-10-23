package cafe.model;

/** Product category (added to the UML model for practical menu organisation). */
public record LoaiMon(Integer maLoai, String tenLoai) {
    @Override
    public String toString() {
        return tenLoai;
    }
}
