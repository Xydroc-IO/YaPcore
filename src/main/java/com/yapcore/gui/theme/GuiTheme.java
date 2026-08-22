package com.yapcore.gui.theme;

import com.formdev.flatlaf.FlatDarkLaf;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;

/** Shared YaPcore control-panel colors / widgets. */
public final class GuiTheme {

    public static final Color BG = new Color(0x16, 0x1B, 0x22);
    public static final Color PANEL = new Color(0x1C, 0x23, 0x2B);
    public static final Color ACCENT = new Color(0x2D, 0xB5, 0x8A);
    public static final Color TEXT = new Color(0xE6, 0xED, 0xF3);
    public static final Color MUTED = new Color(0x8B, 0x9C, 0xAB);
    public static final Color DANGER = new Color(0xC4, 0x4B, 0x4B);

    private GuiTheme() {
    }

    public static void install() {
        try {
            FlatDarkLaf.setup();
            UIManager.put("Button.arc", 10);
            UIManager.put("Component.arc", 10);
            UIManager.put("TextComponent.arc", 8);
            UIManager.put("TabbedPane.tabHeight", 32);
            UIManager.put("ScrollBar.width", 12);
        } catch (Exception e) {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
            }
        }
    }

    public static JPanel card() {
        JPanel p = new JPanel();
        p.setBackground(PANEL);
        p.setBorder(new EmptyBorder(12, 12, 12, 12));
        return p;
    }

    public static JLabel sectionTitle(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Segoe UI", Font.BOLD, 15));
        l.setForeground(TEXT);
        return l;
    }

    /** Tip / blurb that wraps inside the side panel instead of stretching the window. */
    public static JLabel tip(String htmlBody) {
        JLabel tip = new JLabel("<html><body style='width:260px'>" + htmlBody + "</body></html>");
        tip.setForeground(MUTED);
        tip.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        return tip;
    }

    /** Vertical-only scroll — content widths follow the viewport. */
    public static JScrollPane verticalScroll(Component inner) {
        JScrollPane scroll = new JScrollPane(inner);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        scroll.getHorizontalScrollBar().setUnitIncrement(18);
        return scroll;
    }

    public static void stylePrimary(JButton btn) {
        btn.setBackground(ACCENT);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
    }

    public static void styleDanger(JButton btn) {
        btn.setBackground(DANGER);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
    }

    /**
     * Size a window to fit the usable screen (taskbar insets), with soft caps so
     * it opens usable without the user dragging every edge.
     */
    public static void fitWindow(Window window, int preferredW, int preferredH, int minW, int minH) {
        Rectangle bounds = usableScreenBounds(window);
        Insets pad = new Insets(48, 40, 48, 40);
        int maxW = Math.max(minW, bounds.width - pad.left - pad.right);
        int maxH = Math.max(minH, bounds.height - pad.top - pad.bottom);
        int w = Math.min(preferredW, maxW);
        int h = Math.min(preferredH, maxH);
        w = Math.max(minW, Math.min(w, maxW));
        h = Math.max(minH, Math.min(h, maxH));
        window.setMinimumSize(new Dimension(minW, minH));
        window.setSize(w, h);
        window.setLocation(
                bounds.x + Math.max(0, (bounds.width - w) / 2),
                bounds.y + Math.max(0, (bounds.height - h) / 2));
    }

    private static Rectangle usableScreenBounds(Window window) {
        try {
            GraphicsConfiguration gc = window.getGraphicsConfiguration();
            if (gc == null) {
                gc = GraphicsEnvironment.getLocalGraphicsEnvironment()
                        .getDefaultScreenDevice().getDefaultConfiguration();
            }
            Rectangle bounds = gc.getBounds();
            Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(gc);
            return new Rectangle(
                    bounds.x + insets.left,
                    bounds.y + insets.top,
                    bounds.width - insets.left - insets.right,
                    bounds.height - insets.top - insets.bottom);
        } catch (Exception e) {
            Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
            return new Rectangle(0, 0, screen.width, screen.height);
        }
    }
}
