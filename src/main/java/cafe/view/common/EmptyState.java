package cafe.view.common;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;

/** Restrained, reusable empty state for lists and operational queues. */
public final class EmptyState extends JPanel {
    private final JLabel title;
    private final JLabel explanation;

    public EmptyState(String heading, String hint) {
        super(new BorderLayout());
        setOpaque(true);
        setBackground(Theme.SURFACE);
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setBorder(Theme.pad(18, 16));
        content.setLayout(new javax.swing.BoxLayout(content, javax.swing.BoxLayout.Y_AXIS));
        Mark mark = new Mark();
        mark.setAlignmentX(CENTER_ALIGNMENT);
        title = Theme.sectionTitle(heading);
        title.setAlignmentX(CENTER_ALIGNMENT);
        explanation = Theme.muted(wrap(hint));
        explanation.setAlignmentX(CENTER_ALIGNMENT);
        content.add(javax.swing.Box.createVerticalGlue());
        content.add(mark);
        content.add(javax.swing.Box.createVerticalStrut(12));
        content.add(title);
        content.add(javax.swing.Box.createVerticalStrut(5));
        content.add(explanation);
        content.add(javax.swing.Box.createVerticalGlue());
        add(content, BorderLayout.CENTER);
        getAccessibleContext().setAccessibleName(heading);
        getAccessibleContext().setAccessibleDescription(hint);
    }

    public void setContent(String heading, String hint) {
        title.setText(heading);
        explanation.setText(wrap(hint));
        getAccessibleContext().setAccessibleName(heading);
        getAccessibleContext().setAccessibleDescription(hint);
    }

    private static String wrap(String text) {
        String escaped = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return "<html><div style='text-align:center;width:240px'>" + escaped + "</div></html>";
    }

    private static final class Mark extends JComponent {
        Mark() {
            setPreferredSize(new Dimension(38, 38));
            setMinimumSize(getPreferredSize());
            setMaximumSize(getPreferredSize());
            setToolTipText("Danh sách chưa có dữ liệu");
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(0xE9E3DA));
            g.fill(new Ellipse2D.Double(3, 3, 32, 32));
            g.setColor(Theme.MUTED);
            g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawOval(12, 11, 10, 10);
            g.drawLine(21, 21, 27, 27);
            g.dispose();
        }
    }
}
