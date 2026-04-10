package raven.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.ui.FlatUIUtils;
import com.formdev.flatlaf.util.UIScale;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JToggleButton;
import javax.swing.UIManager;

public class SidebarButton extends JToggleButton {

    private boolean active;
    private boolean compact;
    private String fullText;
    private boolean hovered;

    public SidebarButton(String text, Icon icon) {
        super(text, icon);
        init();
    }

    public SidebarButton(Action action) {
        super(action);
        init();
    }

    private void init() {
        fullText = getText();
        setHorizontalAlignment(LEFT);
        setIconTextGap(10);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setOpaque(false);
        setFocusPainted(false);
        setBorderPainted(false);
        setContentAreaFilled(false);
        setRolloverEnabled(false);

        putClientProperty("JButton.buttonType", "borderless");
        putClientProperty("Component.focusWidth", 0);

        setMargin(new Insets(8, 10, 8, 10));
        setFont(getFont().deriveFont(Font.PLAIN, 12f));
        setForeground(UIManager.getColor("Label.foreground"));

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                hovered = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hovered = false;
                repaint();
            }
        });
    }

    public void setActive(boolean active) {
        if (this.active == active) {
            return;
        }
        this.active = active;
        setSelected(active);
        setForeground(active ? Color.white : UIManager.getColor("Label.foreground"));
        repaint();
    }

    public void setCompact(boolean compact) {
        if (this.compact == compact) {
            return;
        }
        this.compact = compact;
        if (compact) {
            setToolTipText(fullText);
            setText("");
            setIconTextGap(0);
        } else {
            setToolTipText(null);
            setText(fullText);
            setIconTextGap(10);
        }
        revalidate();
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int arc = UIScale.scale(8);
            int barW = UIScale.scale(3);

            Color accent = UIManager.getColor("App.accentColor");
            if (accent == null) {
                accent = new Color(59, 130, 246);
            }

            boolean act = isSelected() || active;
            if (act || hovered) {
                Color bg = act ? withAlpha(accent, 0.22f) : withAlpha(accent, 0.12f);
                g2.setColor(bg);
                FlatUIUtils.paintComponentBackground(g2, 0, 0, getWidth(), getHeight(), 0, arc);
            }
            if (act) {
                g2.setColor(accent);
                g2.fillRoundRect(0, UIScale.scale(6), barW, getHeight() - UIScale.scale(12), barW, barW);
            }
        } finally {
            g2.dispose();
        }
        super.paintComponent(g);
    }

    private static Color withAlpha(Color c, float alpha01) {
        int a = Math.min(255, Math.max(0, Math.round(alpha01 * 255f)));
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
    }
}

