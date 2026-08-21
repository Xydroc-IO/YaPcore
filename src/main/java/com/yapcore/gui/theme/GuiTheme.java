package com.yapcore.gui.theme;

import com.formdev.flatlaf.FlatDarkLaf;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import java.awt.Color;
import java.awt.Font;

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
}
