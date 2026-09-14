package com.yapcore.gui.panels.fleet;

import com.yapcore.gui.theme.GuiTheme;
import com.yapcore.server.YaPcoreServer;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.util.List;
import java.util.Map;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;

/** Manage plugins for one fleet instance — catalog install + installed list. */
public final class FleetPluginsDialog extends JDialog {

    private final YaPcoreServer server;
    private final String instanceId;
    private final FleetPluginPicker picker;
    private final DefaultListModel<String> installedModel = new DefaultListModel<>();
    private final JList<String> installedList = new JList<>(installedModel);
    private final JLabel status = new JLabel(" ");

    public FleetPluginsDialog(Frame owner, YaPcoreServer server, String instanceId) {
        super(owner, "Plugins · " + instanceId, true);
        this.server = server;
        this.instanceId = instanceId;
        this.picker = new FleetPluginPicker(server, false);

        JPanel left = new JPanel(new BorderLayout(6, 6));
        left.setBorder(new EmptyBorder(8, 8, 8, 8));
        left.add(GuiTheme.sectionTitle("Catalog → install on " + instanceId), BorderLayout.NORTH);
        left.add(picker, BorderLayout.CENTER);
        JButton install = new JButton("Install selected");
        JButton installCore = new JButton("Install CORE+NETWORK");
        GuiTheme.stylePrimary(install);
        install.addActionListener(e -> installSelected());
        installCore.addActionListener(e -> installCoreNetwork());
        JPanel leftActions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        leftActions.setOpaque(false);
        leftActions.add(install);
        leftActions.add(installCore);
        left.add(leftActions, BorderLayout.SOUTH);

        JPanel right = new JPanel(new BorderLayout(6, 6));
        right.setBorder(new EmptyBorder(8, 8, 8, 8));
        right.add(GuiTheme.sectionTitle("Installed on " + instanceId), BorderLayout.NORTH);
        installedList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        installedList.setBackground(new java.awt.Color(0x0D, 0x11, 0x17));
        installedList.setForeground(GuiTheme.TEXT);
        right.add(new JScrollPane(installedList), BorderLayout.CENTER);
        JPanel rightActions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        rightActions.setOpaque(false);
        JButton enable = new JButton("Enable");
        JButton disable = new JButton("Disable");
        JButton remove = new JButton("Remove");
        JButton refresh = new JButton("Refresh");
        GuiTheme.styleDanger(remove);
        enable.addActionListener(e -> setPluginHardEnabled(true));
        disable.addActionListener(e -> setPluginHardEnabled(false));
        remove.addActionListener(e -> uninstall());
        refresh.addActionListener(e -> reloadInstalled());
        rightActions.add(enable);
        rightActions.add(disable);
        rightActions.add(remove);
        rightActions.add(refresh);
        right.add(rightActions, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        split.setResizeWeight(0.55);
        getContentPane().setLayout(new BorderLayout(8, 8));
        getContentPane().add(split, BorderLayout.CENTER);
        status.setBorder(new EmptyBorder(4, 12, 8, 12));
        getContentPane().add(status, BorderLayout.SOUTH);
        setPreferredSize(new Dimension(860, 520));
        pack();
        setLocationRelativeTo(owner);
        reloadInstalled();
        preselectMissing();
    }

    private void preselectMissing() {
        try {
            server.fleet().listInstancePlugins(instanceId);
        } catch (Exception ignored) {
        }
    }

    private void installSelected() {
        List<String> jars = picker.selectedJars();
        if (jars.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Select one or more catalog jars first.");
            return;
        }
        status.setText("Installing " + jars.size() + " jar(s)…");
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                StringBuilder sb = new StringBuilder();
                for (String jar : jars) {
                    Map<String, Object> r = server.fleet().installCatalogJar(
                            jar, List.of(instanceId), "none");
                    sb.append(jar).append(": ").append(r.get("ok")).append('\n');
                }
                return sb.toString();
            }

            @Override
            protected void done() {
                try {
                    status.setText(get().trim());
                } catch (Exception e) {
                    status.setText(e.getMessage());
                    JOptionPane.showMessageDialog(FleetPluginsDialog.this, e.getMessage());
                }
                reloadInstalled();
            }
        }.execute();
    }

    private void installCoreNetwork() {
        status.setText("Installing CORE+NETWORK…");
        new SwingWorker<Map<String, Object>, Void>() {
            @Override
            protected Map<String, Object> doInBackground() throws Exception {
                return server.fleet().installCoreNetworkDefaults(instanceId);
            }

            @Override
            protected void done() {
                try {
                    Map<String, Object> r = get();
                    status.setText("CORE+NETWORK · " + r.getOrDefault("pluginCount", "?") + " jars");
                } catch (Exception e) {
                    status.setText(e.getMessage());
                    JOptionPane.showMessageDialog(FleetPluginsDialog.this, e.getMessage());
                }
                reloadInstalled();
            }
        }.execute();
    }

    private void reloadInstalled() {
        installedModel.clear();
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> plugins = (List<Map<String, Object>>)
                    server.fleet().listInstancePlugins(instanceId).get("plugins");
            if (plugins != null) {
                for (Map<String, Object> p : plugins) {
                    boolean hard = !Boolean.FALSE.equals(p.get("hardEnabled"));
                    installedModel.addElement(
                            p.getOrDefault("fileName", "?") + (hard ? "" : " [disabled]")
                                    + " (" + p.getOrDefault("sizeLabel", "?") + ")");
                }
            }
            if (installedModel.isEmpty()) {
                installedModel.addElement("(none — install from catalog on the left)");
            }
        } catch (Exception e) {
            installedModel.addElement("Error: " + e.getMessage());
        }
    }

    private String selectedJarName() {
        String selected = installedList.getSelectedValue();
        if (selected == null || selected.startsWith("(") || selected.startsWith("Error")) {
            return null;
        }
        String name = selected.contains(" (") ? selected.substring(0, selected.indexOf(" (")) : selected;
        name = name.replace(" [disabled]", "").trim();
        return name;
    }

    private void setPluginHardEnabled(boolean enable) {
        String jar = selectedJarName();
        if (jar == null) {
            return;
        }
        run(() -> server.fleet().setInstancePluginEnabled(instanceId, jar, enable));
    }

    private void uninstall() {
        String jar = selectedJarName();
        if (jar == null) {
            return;
        }
        if (JOptionPane.showConfirmDialog(this, "Remove " + jar + " from " + instanceId + "?",
                "Remove", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        run(() -> server.fleet().uninstallInstancePlugin(instanceId, jar));
    }

    private void run(Throwing op) {
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                op.run();
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    status.setText("OK");
                } catch (Exception e) {
                    status.setText(e.getMessage());
                    JOptionPane.showMessageDialog(FleetPluginsDialog.this, e.getMessage());
                }
                reloadInstalled();
            }
        }.execute();
    }

    @FunctionalInterface
    private interface Throwing {
        void run() throws Exception;
    }
}
