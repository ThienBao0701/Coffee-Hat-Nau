package cafe.view.common;

import javax.swing.AbstractButton;
import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/** Small, consistent line icons for the main module navigation. */
public final class NavIcon implements Icon {
    private final String module;

    public NavIcon(String module) {
        this.module = module;
    }

    @Override public int getIconWidth() { return 18; }
    @Override public int getIconHeight() { return 18; }

    @Override
    public void paintIcon(Component component, Graphics graphics, int x, int y) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.translate(x, y);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(component instanceof AbstractButton button ? button.getForeground() : Theme.MUTED);
        g.setStroke(new BasicStroke(1.45f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        switch (module) {
            case "Tổng quan" -> dashboard(g);
            case "Bán hàng" -> receipt(g);
            case "Ca thu ngân" -> drawer(g);
            case "Hóa đơn" -> document(g);
            case "Khách hàng & đặt bàn" -> people(g);
            case "Bàn & khu vực" -> tables(g);
            case "Thực đơn & giá" -> cup(g);
            case "Khuyến mãi & thuế" -> tag(g);
            case "Kho hàng" -> box(g);
            case "Nhân viên" -> person(g);
            case "Báo cáo" -> chart(g);
            case "Nhật ký hệ thống" -> audit(g);
            default -> dashboard(g);
        }
        g.dispose();
    }

    private static void dashboard(Graphics2D g) {
        g.drawRoundRect(2, 2, 5, 5, 1, 1);
        g.drawRoundRect(11, 2, 5, 5, 1, 1);
        g.drawRoundRect(2, 11, 5, 5, 1, 1);
        g.drawRoundRect(11, 11, 5, 5, 1, 1);
    }

    private static void receipt(Graphics2D g) {
        g.drawRoundRect(3, 2, 12, 14, 2, 2);
        g.drawLine(6, 6, 12, 6);
        g.drawLine(6, 9, 12, 9);
        g.drawLine(6, 12, 10, 12);
    }

    private static void drawer(Graphics2D g) {
        g.drawRoundRect(2, 5, 14, 10, 2, 2);
        g.drawLine(2, 8, 16, 8);
        g.drawRoundRect(6, 10, 6, 3, 1, 1);
        g.drawLine(4, 3, 14, 3);
    }

    private static void document(Graphics2D g) {
        Path2D page = new Path2D.Float();
        page.moveTo(4, 2); page.lineTo(11, 2); page.lineTo(15, 6); page.lineTo(15, 16);
        page.lineTo(4, 16); page.closePath(); g.draw(page);
        g.drawLine(11, 2, 11, 6); g.drawLine(11, 6, 15, 6);
        g.drawLine(6, 9, 13, 9); g.drawLine(6, 12, 13, 12);
    }

    private static void people(Graphics2D g) {
        g.draw(new Ellipse2D.Float(3, 3, 5, 5));
        g.draw(new Ellipse2D.Float(10, 4, 4, 4));
        g.drawArc(1, 9, 10, 8, 0, 180);
        g.drawArc(9, 10, 8, 7, 0, 180);
    }

    private static void tables(Graphics2D g) {
        g.drawRoundRect(3, 3, 12, 12, 2, 2);
        g.drawLine(9, 3, 9, 15); g.drawLine(3, 9, 15, 9);
    }

    private static void cup(Graphics2D g) {
        g.drawRoundRect(3, 7, 11, 8, 2, 2);
        g.drawArc(12, 8, 5, 5, 270, 180);
        g.drawLine(4, 16, 15, 16);
        g.drawArc(5, 1, 3, 5, 20, 120);
        g.drawArc(10, 1, 3, 5, 20, 120);
    }

    private static void tag(Graphics2D g) {
        Path2D tag = new Path2D.Float();
        tag.moveTo(2, 5); tag.lineTo(10, 2); tag.lineTo(16, 8); tag.lineTo(8, 16); tag.closePath(); g.draw(tag);
        g.draw(new Ellipse2D.Float(10, 5, 2, 2));
    }

    private static void box(Graphics2D g) {
        Path2D crate = new Path2D.Float();
        crate.moveTo(2, 5); crate.lineTo(9, 2); crate.lineTo(16, 5); crate.lineTo(16, 13);
        crate.lineTo(9, 16); crate.lineTo(2, 13); crate.closePath(); g.draw(crate);
        g.drawLine(2, 5, 9, 8); g.drawLine(16, 5, 9, 8); g.drawLine(9, 8, 9, 16);
    }

    private static void person(Graphics2D g) {
        g.draw(new Ellipse2D.Float(6, 2, 6, 6));
        g.draw(new RoundRectangle2D.Float(3, 10, 12, 7, 4, 4));
    }

    private static void chart(Graphics2D g) {
        g.drawLine(2, 16, 16, 16);
        g.drawRoundRect(4, 9, 3, 7, 1, 1);
        g.drawRoundRect(9, 5, 3, 11, 1, 1);
        g.drawRoundRect(14, 2, 3, 14, 1, 1);
    }

    private static void audit(Graphics2D g) {
        g.draw(new Ellipse2D.Float(2, 2, 14, 14));
        g.drawLine(9, 5, 9, 9); g.drawLine(9, 9, 12, 11);
    }
}
