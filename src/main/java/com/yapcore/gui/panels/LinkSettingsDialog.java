package com.yapcore.gui.panels;

import com.yapcore.config.ServerConfig;
import com.yapcore.gui.theme.GuiTheme;
import com.yapcore.server.YaPcoreServer;
import com.yapcore.web.DashboardLinkSnapshot;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Edit {@code link.properties} backends and core proxy settings. */
final class LinkSettingsDialog extends JDialog {

    private final YaPcoreServer server;
    private final JTextField bindField = new JTextField();
    private final JTextField motdField = new JTextField();
    private final JTextField maxPlayersField = new JTextField();
    private final JCheckBox onlineModeBox = new JCheckBox("Online mode (Mojang auth)");
    private final JTextField tryField = new JTextField();
    private final DefaultTableModel serversModel = new DefaultTableModel(
            new String[]{"Name", "Address (host:port)", "Bedrock (optional)"}, 0);
    private final JTable serversTable = new JTable(serversModel);
    private final DefaultTableModel forcedModel = new DefaultTableModel(new String[]{"Hostname", "Backend"}, 0);
    private final JTable forcedTable = new JTable(forcedModel);

    LinkSettingsDialog(Frame owner, YaPcoreServer server) {
        super(owner, "YaP Link settings", true);
        this.server = server;
        setLayout(new BorderLayout(8, 8));
        add(buildForm(), BorderLayout.CENTER);
        add(buildButtons(), BorderLayout.SOUTH);
        loadFromDisk();
        pack();
        setMinimumSize(getSize());
        setLocationRelativeTo(owner);
    }

    private JPanel buildForm() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(4, 4, 4, 4);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;

        panel.add(fieldRow("Bind", bindField), c);
        c.gridy++;
        panel.add(fieldRow("MOTD", motdField), c);
        c.gridy++;
        panel.add(fieldRow("Max players", maxPlayersField), c);
        c.gridy++;
        panel.add(onlineModeBox, c);
        c.gridy++;
        panel.add(GuiTheme.sectionTitle("Backends"), c);
        c.gridy++;
        panel.add(new JScrollPane(serversTable), c);
        c.gridy++;
        JPanel srvBtns = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 0));
        srvBtns.setOpaque(false);
        JButton addSrv = new JButton("Add backend");
        addSrv.addActionListener(e -> serversModel.addRow(new Object[]{"hub", "127.0.0.1:25566", ""}));
        JButton rmSrv = new JButton("Remove selected");
        rmSrv.addActionListener(e -> {
            int row = serversTable.getSelectedRow();
            if (row >= 0) {
                serversModel.removeRow(row);
            }
        });
        srvBtns.add(addSrv);
        srvBtns.add(rmSrv);
        panel.add(srvBtns, c);
        c.gridy++;
        panel.add(fieldRow("Try order (comma-separated)", tryField), c);
        c.gridy++;
        panel.add(GuiTheme.sectionTitle("Forced hosts"), c);
        c.gridy++;
        panel.add(new JScrollPane(forcedTable), c);
        c.gridy++;
        JPanel fhBtns = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 0));
        fhBtns.setOpaque(false);
        JButton addFh = new JButton("Add forced host");
        addFh.addActionListener(e -> forcedModel.addRow(new Object[]{"hub.example.com", "hub"}));
        JButton rmFh = new JButton("Remove selected");
        rmFh.addActionListener(e -> {
            int row = forcedTable.getSelectedRow();
            if (row >= 0) {
                forcedModel.removeRow(row);
            }
        });
        fhBtns.add(addFh);
        fhBtns.add(rmFh);
        panel.add(fhBtns, c);
        return panel;
    }

    private JPanel buildButtons() {
        JPanel row = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 8));
        row.setOpaque(false);
        JButton save = new JButton("Save");
        GuiTheme.stylePrimary(save);
        save.addActionListener(e -> saveToDisk());
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> dispose());
        row.add(cancel);
        row.add(save);
        return row;
    }

    private static JPanel fieldRow(String label, JTextField field) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        JLabel l = new JLabel(label);
        l.setForeground(GuiTheme.MUTED);
        row.add(l, BorderLayout.WEST);
        row.add(field, BorderLayout.CENTER);
        return row;
    }

    @SuppressWarnings("unchecked")
    private void loadFromDisk() {
        ServerConfig cfg = server.getConfig();
        Path root = server.getRootDir();
        Map<String, Object> snap = DashboardLinkSnapshot.snapshot(
                root, cfg.getLinkEmbedHome(), cfg.isLinkEmbed(), cfg.isVelocityEnabled());
        bindField.setText(String.valueOf(snap.getOrDefault("bind", "0.0.0.0:25565")));
        motdField.setText(String.valueOf(snap.getOrDefault("motd", "YaP Link")));
        maxPlayersField.setText(String.valueOf(snap.getOrDefault("maxPlayers", "500")));
        onlineModeBox.setSelected(Boolean.TRUE.equals(snap.get("onlineMode")));

        serversModel.setRowCount(0);
        List<Map<String, Object>> servers = (List<Map<String, Object>>) snap.get("servers");
        if (servers == null || servers.isEmpty()) {
            serversModel.addRow(new Object[]{"hub", "127.0.0.1:25566", ""});
        } else {
            for (Map<String, Object> s : servers) {
                serversModel.addRow(new Object[]{
                        s.get("name"), s.get("address"), s.getOrDefault("bedrock", "")});
            }
        }
        List<String> tryList = (List<String>) snap.get("tryServers");
        tryField.setText(tryList == null ? "" : String.join(", ", tryList));

        forcedModel.setRowCount(0);
        List<Map<String, Object>> forced = (List<Map<String, Object>>) snap.get("forcedHosts");
        if (forced != null) {
            for (Map<String, Object> f : forced) {
                forcedModel.addRow(new Object[]{f.get("host"), f.get("server")});
            }
        }
    }

    private void saveToDisk() {
        try {
            ServerConfig cfg = server.getConfig();
            Path root = server.getRootDir();
            String linkHome = cfg.getLinkEmbedHome();

            Map<String, String> proxy = new LinkedHashMap<>();
            proxy.put("bind", bindField.getText().trim());
            proxy.put("motd", motdField.getText().trim());
            proxy.put("max-players", maxPlayersField.getText().trim());
            proxy.put("online-mode", onlineModeBox.isSelected() ? "true" : "false");
            DashboardLinkSnapshot.saveProxySettings(root, linkHome, proxy);

            List<Map<String, String>> servers = new ArrayList<>();
            for (int i = 0; i < serversModel.getRowCount(); i++) {
                Map<String, String> row = new LinkedHashMap<>();
                row.put("name", String.valueOf(serversModel.getValueAt(i, 0)).trim());
                row.put("address", String.valueOf(serversModel.getValueAt(i, 1)).trim());
                row.put("bedrock", String.valueOf(serversModel.getValueAt(i, 2)).trim());
                servers.add(row);
            }
            List<String> tryOrder = new ArrayList<>();
            for (String part : tryField.getText().split("[,\\s]+")) {
                if (!part.isBlank()) {
                    tryOrder.add(part.trim());
                }
            }
            List<Map<String, String>> forced = new ArrayList<>();
            for (int i = 0; i < forcedModel.getRowCount(); i++) {
                Map<String, String> row = new LinkedHashMap<>();
                row.put("host", String.valueOf(forcedModel.getValueAt(i, 0)).trim());
                row.put("server", String.valueOf(forcedModel.getValueAt(i, 1)).trim());
                forced.add(row);
            }
            DashboardLinkSnapshot.saveServersConfig(root, linkHome, servers, tryOrder, forced);

            var linkProc = server.getLinkProcess();
            if (linkProc.isRunning()) {
                linkProc.dispatchCommand("reload");
            }
            dispose();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Save Link settings", JOptionPane.ERROR_MESSAGE);
        }
    }
}
