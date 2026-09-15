package com.yapcore.gui;

import com.yapcore.gui.theme.GuiTheme;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

/** Header, console, and status-tab builders for {@link ControlPanel}. */
final class ControlPanelChrome {

    private ControlPanelChrome() {
    }

    static JPanel buildHeader(
            ControlPanelFleetContext fleetCtx,
            JLabel subtitle,
            JLabel javaJoinLabel,
            JButton startBtn,
            JButton stopBtn,
            JButton restartBtn,
            Runnable openTestLab,
            Runnable openDashboard,
            Runnable onStart,
            Runnable onStop,
            Runnable onRestart) {
        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setOpaque(false);
        JLabel brand = new JLabel("YaPcore");
        brand.setFont(new Font("Segoe UI", Font.BOLD, 24));
        brand.setForeground(GuiTheme.ACCENT);
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        subtitle.setForeground(GuiTheme.MUTED);
        JPanel titles = new JPanel(new GridBagLayout());
        titles.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.anchor = GridBagConstraints.WEST;
        titles.add(brand, c);
        c.gridy = 1;
        titles.add(subtitle, c);
        c.gridy = 2;
        c.insets = new Insets(6, 0, 0, 0);
        JPanel ctxRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        ctxRow.setOpaque(false);
        JLabel ctxLabel = new JLabel(fleetCtx.fleetEnabled() ? "Selected server" : "Mode");
        ctxLabel.setForeground(GuiTheme.MUTED);
        ctxRow.add(ctxLabel);
        if (fleetCtx.fleetEnabled()) {
            fleetCtx.combo().setToolTipText(
                    "Fleet home = all servers. Or pick one server (same as left rail).");
        }
        ctxRow.add(fleetCtx.combo());
        titles.add(ctxRow, c);
        c.gridy = 3;
        c.insets = new Insets(4, 0, 0, 0);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        javaJoinLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        javaJoinLabel.setForeground(GuiTheme.TEXT);
        titles.add(javaJoinLabel, c);
        header.add(titles, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        JButton testLabBtn = new JButton("Test Lab");
        GuiTheme.stylePrimary(testLabBtn);
        testLabBtn.addActionListener(e -> openTestLab.run());
        JButton dashboardBtn = new JButton("Web Dashboard");
        GuiTheme.stylePrimary(dashboardBtn);
        dashboardBtn.addActionListener(e -> openDashboard.run());
        GuiTheme.stylePrimary(startBtn);
        GuiTheme.styleDanger(stopBtn);
        restartBtn.addActionListener(e -> onRestart.run());
        startBtn.addActionListener(e -> onStart.run());
        stopBtn.addActionListener(e -> onStop.run());
        actions.add(testLabBtn);
        actions.add(dashboardBtn);
        actions.add(startBtn);
        actions.add(stopBtn);
        actions.add(restartBtn);
        header.add(actions, BorderLayout.EAST);
        return header;
    }

    static JPanel buildConsolePanel(
            JLabel consoleTitle,
            JTextArea console,
            JTextField commandInput,
            Runnable onSubmit) {
        JPanel panel = GuiTheme.card();
        panel.setLayout(new BorderLayout(8, 8));
        panel.setMinimumSize(new Dimension(320, 240));
        panel.add(consoleTitle, BorderLayout.NORTH);
        consoleTitle.setFont(new Font("Segoe UI", Font.BOLD, 13));
        consoleTitle.setForeground(GuiTheme.TEXT);
        console.setEditable(false);
        console.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        console.setBackground(new Color(0x0D, 0x11, 0x17));
        console.setForeground(GuiTheme.TEXT);
        console.setCaretColor(GuiTheme.ACCENT);
        console.setLineWrap(true);
        console.setWrapStyleWord(true);
        panel.add(new JScrollPane(console), BorderLayout.CENTER);
        JPanel inputRow = new JPanel(new BorderLayout(8, 0));
        inputRow.setOpaque(false);
        commandInput.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        JButton send = new JButton("Send");
        GuiTheme.stylePrimary(send);
        send.addActionListener(e -> onSubmit.run());
        inputRow.add(commandInput, BorderLayout.CENTER);
        inputRow.add(send, BorderLayout.EAST);
        panel.add(inputRow, BorderLayout.SOUTH);
        return panel;
    }

    static JPanel buildStatusTab(
            JLabel statusLabel,
            JLabel playersLabel,
            JLabel heapLabel,
            JLabel ticksLabel,
            JLabel dualStackLabel,
            JLabel activePackLabel) {
        JPanel runtime = GuiTheme.card();
        runtime.setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(6, 4, 6, 4);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        runtime.add(GuiTheme.sectionTitle("Runtime"), c);
        c.gridy++;
        runtime.add(kv("State", statusLabel), c);
        c.gridy++;
        runtime.add(kv("Players", playersLabel), c);
        c.gridy++;
        runtime.add(kv("Heap", heapLabel), c);
        c.gridy++;
        runtime.add(kv("Ticks", ticksLabel), c);
        c.gridy++;
        runtime.add(kv("Clients", dualStackLabel), c);
        c.gridy++;
        runtime.add(kv("Active packs", activePackLabel), c);
        return runtime;
    }

    static String escapeHtml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static JPanel kv(String key, JLabel value) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        JLabel k = new JLabel(key);
        k.setForeground(GuiTheme.MUTED);
        value.setForeground(GuiTheme.TEXT);
        value.setFont(new Font("Segoe UI", Font.BOLD, 14));
        row.add(k, BorderLayout.WEST);
        row.add(value, BorderLayout.EAST);
        return row;
    }
}
