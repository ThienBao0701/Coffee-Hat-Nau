package cafe;

import cafe.model.InvoiceDetail;
import cafe.model.PhuongThucThanhToan;
import cafe.service.OrderService;
import cafe.service.Services;
import cafe.util.Session;
import cafe.view.sales.ReceiptFormatter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ReceiptFormatterTest {

    @AfterEach
    void tearDown() {
        Session.logout();
    }

    @Test
    void paidReceiptShowsBreakdownAndFitsPaperWidth() {
        Services s = TestSupport.newServices();
        TestSupport.createAccounts(s);
        TestSupport.loginStaff(s);
        int hd = s.orders.openTableOrder(1);
        s.orders.addItem(hd, 2, 2, 2, Map.of(4, 1), "ít đá");
        String draft = ReceiptFormatter.format(s.orders.getInvoice(hd), "Cà Phê Test", "1 Đường A", "0901");
        assertTrue(draft.contains("PHIẾU TẠM TÍNH"));

        InvoiceDetail paid = s.orders.pay(new OrderService.PaymentRequest(hd, null, null, PhuongThucThanhToan.TIEN_MAT, new BigDecimal("100000")));
        String r = ReceiptFormatter.format(paid, "Cà Phê Test", "1 Đường A", "0901");
        assertTrue(r.contains("HÓA ĐƠN THANH TOÁN"));
        assertTrue(r.contains("Cà phê sữa (M)"));
        assertTrue(r.contains("+ Shot espresso"));
        assertTrue(r.contains("* ít đá"));
        assertTrue(r.contains("TỔNG CỘNG:") && r.contains("79.920 đ"), r);    // 74,000 + 8% VAT
        assertTrue(r.contains("Tiền thối:") && r.contains("20.080 đ"), r);
        for (String line : r.split("\n")) {
            assertTrue(line.length() <= 42, "line too long: " + line);
        }
    }
}
