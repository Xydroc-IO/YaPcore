package com.yapcore.gui.panels;

import com.yapcore.gui.theme.GuiTheme;
import com.yapcore.network.publicity.LocalJoinAddresses;
import com.yapcore.network.publicity.PublicEndpoint;
import com.yapcore.server.YaPcoreServer;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;

/** Live join addresses — same-PC + LAN + public/crossplay. */
public final class ConnectInfoPanel {

    private final YaPcoreServer server;
    private final JPanel root = new JPanel(new BorderLayout(6, 6));
    private final JTextField thisPcField = addressField();
    private final JTextField localhostField = addressField();
    private final JTextField lanField = addressField();
    private final JTextField crossplayField = addressField();
    private final JTextField packField = addressField();
    private final JLabel modeLabel = new JLabel("—");
    private final JLabel tipLabel = new JLabel();

    public ConnectInfoPanel(YaPcoreServer server) {
        this.server = server;
        root.setOpaque(false);
        root.add(buildBody(), BorderLayout.CENTER);
        refresh();
    }

    public JPanel component() {
        return root;
    }

    public void refresh() {
        PublicEndpoint ep = server.publicEndpoint();
        LocalJoinAddresses local = new LocalJoinAddresses(server.getConfig());
        thisPcField.setText(local.loopback());
        localhostField.setText(local.localhostName());
        lanField.setText(PublicEndpoint.guessLocalIpv4().orElse("127.0.0.1")
                + ":" + server.getConfig().getPort());
        crossplayField.setText(ep.crossplayJoinAddress());
        packField.setText(ep.packBaseUrl() + "/pack/…");
        tipLabel.setText("<html><body style='width:240px'>" + local.tip() + "</body></html>");
        if (server.getConfig().isAllowLocalhost()) {
            modeLabel.setText("Same-PC OK · 127.0.0.1");
            modeLabel.setForeground(GuiTheme.ACCENT);
        } else {
            modeLabel.setText("Localhost assist off");
            modeLabel.setForeground(GuiTheme.MUTED);
        }
    }

    private JPanel buildBody() {
        JPanel panel = GuiTheme.card();
        panel.setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.insets = new Insets(3, 2, 3, 2);
        panel.add(GuiTheme.sectionTitle("How to connect"), c);
        c.gridy++;
        JPanel modeRow = new JPanel(new BorderLayout());
        modeRow.setOpaque(false);
        JLabel modeKey = new JLabel("Mode");
        modeKey.setForeground(GuiTheme.MUTED);
        modeLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        modeRow.add(modeKey, BorderLayout.WEST);
        modeRow.add(modeLabel, BorderLayout.EAST);
        panel.add(modeRow, c);
        c.gridy++;
        panel.add(row("This PC (use this)", thisPcField), c);
        c.gridy++;
        panel.add(row("localhost", localhostField), c);
        c.gridy++;
        panel.add(row("LAN / other devices", lanField), c);
        c.gridy++;
        panel.add(row("Public / domain", crossplayField), c);
        c.gridy++;
        panel.add(row("Resource packs", packField), c);
        c.gridy++;
        tipLabel.setForeground(GuiTheme.MUTED);
        tipLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        panel.add(tipLabel, c);
        return panel;
    }

    private static JPanel row(String label, JTextField field) {
        JPanel col = new JPanel(new BorderLayout(4, 2));
        col.setOpaque(false);
        JLabel l = new JLabel(label);
        l.setForeground(GuiTheme.MUTED);
        l.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        col.add(l, BorderLayout.NORTH);
        JPanel line = new JPanel(new BorderLayout(4, 0));
        line.setOpaque(false);
        line.add(field, BorderLayout.CENTER);
        JButton copy = new JButton("Copy");
        copy.setMargin(new Insets(2, 8, 2, 8));
        copy.setFocusPainted(false);
        copy.addActionListener(e -> {
            String text = field.getText();
            if (text != null && !text.isBlank()) {
                Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new StringSelection(text.trim()), null);
                copy.setText("Copied");
                Timer t = new Timer(1200, ev -> copy.setText("Copy"));
                t.setRepeats(false);
                t.start();
            }
        });
        line.add(copy, BorderLayout.EAST);
        col.add(line, BorderLayout.CENTER);
        return col;
    }

    private static JTextField addressField() {
        JTextField f = new JTextField();
        f.setEditable(false);
        f.setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
        f.setForeground(GuiTheme.TEXT);
        f.setBackground(GuiTheme.BG);
        f.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0x2A, 0x35, 0x40)),
                new EmptyBorder(4, 6, 4, 6)));
        f.setPreferredSize(new Dimension(180, 28));
        return f;
    }
}
