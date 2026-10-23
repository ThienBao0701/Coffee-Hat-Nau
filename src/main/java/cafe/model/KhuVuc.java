package cafe.model;

public record KhuVuc(Integer maKhuVuc, String tenKhuVuc) {
    @Override
    public String toString() {
        return tenKhuVuc;
    }
}
