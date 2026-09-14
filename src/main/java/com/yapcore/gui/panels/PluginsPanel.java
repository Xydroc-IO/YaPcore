package com.yapcore.gui.panels;

import com.yapcore.gui.panels.fleet.FleetPluginPicker;
import com.yapcore.gui.panels.fleet.FleetPluginsDialog;
import com.yapcore.gui.theme.GuiTheme;
import com.yapcore.plugin.PluginManager;
import com.yapcore.server.YaPcoreServer;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Dual-pane plugins: catalog (upload + install to servers) and per-instance installed set.
 */
public final class PluginsPanel {

    private static final Logger LOG = Logger.getLogger("YaPcore.GUI.Plugins");

    private final YaPcoreServer server;
    private final DefaultListModel<String> catalogModel = new DefaultListModel<>();
    private final JList<String> catalogList = new JList<>(catalogModel);
    private final DefaultListModel<String> installedModel = new DefaultListModel<>();
    private final JList<String> installedList = new JList<>(installedModel);
    private final JComboBox<String> instanceCombo = new JComboBox<>();
    private final JLabel installedTitle = new JLabel("Installed on —");
    private final JPanel root;
    private String fleetInstanceId;

    public PluginsPanel(YaPcoreServer server) {
        this.server = server;
        root = new JPanel(new BorderLayout(8, 8));
        root.setOpaque(false);

        JPanel catalogPane = GuiTheme.card();
        catalogPane.setLayout(new BorderLayout(8, 8));
        catalogPane.add(GuiTheme.sectionTitle("Available jars (catalog)"), BorderLayout.NORTH);
        catalogList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        catalogList.setBackground(new java.awt.Color(0x0D, 0x11, 0x17));
        catalogList.setForeground(GuiTheme.TEXT);
        catalogPane.add(new JScrollPane(catalogList), BorderLayout.CENTER);
        JPanel catalogBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        catalogBtns.setOpaque(false);
        JButton upload = new JButton("Upload to catalog…");
        JButton installSel = new JButton("Install to this server");
        JButton installMulti = new JButton("Install to…");
        JButton installCore = new JButton("Install CORE+NETWORK");
        JButton refreshCat = new JButton("Refresh");
        GuiTheme.stylePrimary(installSel);
        upload.addActionListener(e -> uploadToCatalog());
        installSel.addActionListener(e -> installCatalogToCurrent());
        installMulti.addActionListener(e -> installCatalogToPicker());
        installCore.addActionListener(e -> installCoreNetwork());
        refreshCat.addActionListener(e -> refreshCatalog());
        catalogBtns.add(upload);
        catalogBtns.add(installSel);
        catalogBtns.add(installMulti);
        catalogBtns.add(installCore);
        catalogBtns.add(refreshCat);
        catalogPane.add(catalogBtns, BorderLayout.SOUTH);

        JPanel instPane = GuiTheme.card();
        instPane.setLayout(new BorderLayout(8, 8));
        JPanel instHead = new JPanel(new BorderLayout(8, 0));
        instHead.setOpaque(false);
        installedTitle.setForeground(GuiTheme.TEXT);
        instHead.add(installedTitle, BorderLayout.CENTER);
        instanceCombo.addActionListener(e -> onInstanceCombo());
        instHead.add(instanceCombo, BorderLayout.EAST);
        instPane.add(instHead, BorderLayout.NORTH);
        installedList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        installedList.setBackground(new java.awt.Color(0x0D, 0x11, 0x17));
        installedList.setForeground(GuiTheme.TEXT);
        instPane.add(new JScrollPane(installedList), BorderLayout.CENTER);
        JPanel instBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        instBtns.setOpaque(false);
        JButton enable = new JButton("Enable");
        JButton disable = new JButton("Disable");
        JButton remove = new JButton("Remove");
        JButton manage = new JButton("Full manager…");
        JButton refreshInst = new JButton("Refresh");
        GuiTheme.styleDanger(remove);
        GuiTheme.stylePrimary(manage);
        enable.addActionListener(e -> setInstalledHardEnabled(true));
        disable.addActionListener(e -> setInstalledHardEnabled(false));
        remove.addActionListener(e -> removeInstalled());
        manage.addActionListener(e -> openFullManager());
        refreshInst.addActionListener(e -> refreshInstalled());
        instBtns.add(enable);
        instBtns.add(disable);
        instBtns.add(remove);
        instBtns.add(manage);
        instBtns.add(refreshInst);
        instPane.add(instBtns, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, catalogPane, instPane);
        split.setResizeWeight(0.45);
        split.setBorder(BorderFactory.createEmptyBorder());
        root.add(split, BorderLayout.CENTER);
        refresh();
    }

    public JPanel component() {
        return root;
    }

    public void setFleetInstance(String instanceId) {
        this.fleetInstanceId = instanceId == null || instanceId.isBlank() ? null : instanceId.trim();
        refreshInstanceCombo();
        if (fleetInstanceId != null) {
            instanceCombo.setSelectedItem(fleetInstanceId);
        }
        refreshInstalled();
    }

    public void refresh() {
        refreshCatalog();
        refreshInstanceCombo();
        refreshInstalled();
    }

    private void refreshCatalog() {
        catalogModel.clear();
        for (PluginManager.PluginInfo p : server.getPluginManager().listPlugins()) {
            catalogModel.addElement(p.fileName() + " (" + p.sizeLabel() + ")");
        }
    }

    private void refreshInstanceCombo() {
        String prev = fleetInstanceId;
        instanceCombo.removeAllItems();
        if (!server.getConfig().isFleetEnabled()) {
            instanceCombo.addItem("(fleet off — catalog only)");
            return;
        }
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> instances = (List<Map<String, Object>>)
                    server.fleet().statusSnapshot().get("instances");
            if (instances != null) {
                for (Map<String, Object> i : instances) {
                    instanceCombo.addItem(String.valueOf(i.get("id")));
                }
            }
        } catch (Exception e) {
            instanceCombo.addItem("(error)");
        }
        if (prev != null) {
            instanceCombo.setSelectedItem(prev);
        }
    }

    private void onInstanceCombo() {
        Object v = instanceCombo.getSelectedItem();
        if (v == null || String.valueOf(v).startsWith("(")) {
            fleetInstanceId = null;
        } else {
            fleetInstanceId = String.valueOf(v);
        }
        refreshInstalled();
    }

    private void refreshInstalled() {
        installedModel.clear();
        if (fleetInstanceId == null || !server.getConfig().isFleetEnabled()) {
            installedTitle.setText("On this server — pick a game server");
            installedModel.addElement("Select a server in the left rail (or the combo above)");
            return;
        }
        installedTitle.setText("On this server · " + fleetInstanceId);
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> plugins = (List<Map<String, Object>>)
                    server.fleet().listInstancePlugins(fleetInstanceId).get("plugins");
            if (plugins != null) {
                for (Map<String, Object> p : plugins) {
                    boolean hard = !Boolean.FALSE.equals(p.get("hardEnabled"));
                    installedModel.addElement(
                            p.getOrDefault("fileName", "?") + (hard ? "" : " [disabled]")
                                    + " (" + p.getOrDefault("sizeLabel", "?") + ")");
                }
            }
            if (installedModel.isEmpty()) {
                installedModel.addElement("(empty — click Install CORE+NETWORK)");
            }
        } catch (Exception e) {
            installedModel.addElement("Error: " + e.getMessage());
        }
    }

    private void installCoreNetwork() {
        if (fleetInstanceId == null) {
            JOptionPane.showMessageDialog(root, "Select a game server first.");
            return;
        }
        try {
            Map<String, Object> r = server.fleet().installCoreNetworkDefaults(fleetInstanceId);
            JOptionPane.showMessageDialog(root,
                    "CORE+NETWORK on " + fleetInstanceId + ": "
                            + r.getOrDefault("pluginCount", "?") + " jar(s)");
            refreshInstalled();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(root, e.getMessage(), "CORE+NETWORK",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private List<String> selectedCatalogJars() {
        List<String> out = new ArrayList<>();
        for (String selected : catalogList.getSelectedValuesList()) {
            out.add(selected.contains(" (") ? selected.substring(0, selected.indexOf(" (")) : selected);
        }
        return out;
    }

    private void uploadToCatalog() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Upload plugin JAR into catalog");
        chooser.setFileFilter(new FileNameExtensionFilter("Plugin jars (*.jar, *.yap)", "jar", "yap"));
        if (chooser.showOpenDialog(root) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path selected = chooser.getSelectedFile().toPath();
        try {
            PluginManager.PluginInfo info = server.getPluginManager().addPlugin(selected);
            LOG.info("Catalog + " + info.fileName());
            refreshCatalog();
            JOptionPane.showMessageDialog(root,
                    "Added to catalog: " + info.fileName()
                            + "\nSelect it and Install to a server.",
                    "Catalog", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(root, e.getMessage(), "Upload", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void installCatalogToCurrent() {
        if (fleetInstanceId == null) {
            JOptionPane.showMessageDialog(root, "Select a target server in the combo (right/top).");
            return;
        }
        List<String> jars = selectedCatalogJars();
        if (jars.isEmpty()) {
            JOptionPane.showMessageDialog(root, "Select one or more jars in the catalog.");
            return;
        }
        installJars(jars, List.of(fleetInstanceId));
    }

    private void installCatalogToPicker() {
        List<String> jars = selectedCatalogJars();
        if (jars.isEmpty()) {
            JOptionPane.showMessageDialog(root, "Select one or more jars in the catalog.");
            return;
        }
        List<String> targets = pickTargetInstances();
        if (targets.isEmpty()) {
            return;
        }
        installJars(jars, targets);
    }

    private List<String> pickTargetInstances() {
        List<String> ids = new ArrayList<>();
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> instances = (List<Map<String, Object>>)
                    server.fleet().statusSnapshot().get("instances");
            if (instances == null || instances.isEmpty()) {
                JOptionPane.showMessageDialog(root, "No fleet instances.");
                return List.of();
            }
            JPanel form = new JPanel();
            form.setLayout(new javax.swing.BoxLayout(form, javax.swing.BoxLayout.Y_AXIS));
            List<javax.swing.JCheckBox> boxes = new ArrayList<>();
            for (Map<String, Object> i : instances) {
                String id = String.valueOf(i.get("id"));
                javax.swing.JCheckBox box = new javax.swing.JCheckBox(id, id.equals(fleetInstanceId));
                boxes.add(box);
                form.add(box);
            }
            int ok = JOptionPane.showConfirmDialog(root, new JScrollPane(form),
                    "Install to servers", JOptionPane.OK_CANCEL_OPTION);
            if (ok != JOptionPane.OK_OPTION) {
                return List.of();
            }
            for (javax.swing.JCheckBox box : boxes) {
                if (box.isSelected()) {
                    ids.add(box.getText());
                }
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(root, e.getMessage());
        }
        return ids;
    }

    private void installJars(List<String> jars, List<String> targets) {
        try {
            for (String jar : jars) {
                server.fleet().installCatalogJar(jar, targets, "none");
            }
            JOptionPane.showMessageDialog(root,
                    "Installed " + jars.size() + " jar(s) → " + String.join(", ", targets));
            refreshInstalled();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(root, e.getMessage(), "Install", JOptionPane.ERROR_MESSAGE);
        }
    }

    private String selectedInstalledJar() {
        String selected = installedList.getSelectedValue();
        if (selected == null || selected.startsWith("(") || selected.startsWith("Error")
                || selected.startsWith("Pick")) {
            return null;
        }
        String name = selected.contains(" (") ? selected.substring(0, selected.indexOf(" (")) : selected;
        return name.replace(" [disabled]", "").trim();
    }

    private void setInstalledHardEnabled(boolean enable) {
        String jar = selectedInstalledJar();
        if (jar == null || fleetInstanceId == null) {
            return;
        }
        try {
            server.fleet().setInstancePluginEnabled(fleetInstanceId, jar, enable);
            refreshInstalled();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(root, e.getMessage());
        }
    }

    private void removeInstalled() {
        String jar = selectedInstalledJar();
        if (jar == null || fleetInstanceId == null) {
            return;
        }
        if (JOptionPane.showConfirmDialog(root, "Remove " + jar + " from " + fleetInstanceId + "?",
                "Remove", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            server.fleet().uninstallInstancePlugin(fleetInstanceId, jar);
            refreshInstalled();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(root, e.getMessage());
        }
    }

    private void openFullManager() {
        if (fleetInstanceId == null) {
            JOptionPane.showMessageDialog(root, "Select a server first.");
            return;
        }
        Frame frame = (Frame) SwingUtilities.getWindowAncestor(root);
        new FleetPluginsDialog(frame, server, fleetInstanceId).setVisible(true);
        refreshInstalled();
        refreshCatalog();
    }
}
