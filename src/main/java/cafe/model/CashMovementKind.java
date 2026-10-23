package cafe.model;

public enum CashMovementKind {
    THEM("Thêm tiền"), CHI("Chi tiền"), RUT("Rút / nộp tiền");

    private final String label;

    CashMovementKind(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
