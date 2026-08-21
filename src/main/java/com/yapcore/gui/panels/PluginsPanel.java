package com.yapcore.gui.panels;

import com.yapcore.gui.theme.GuiTheme;
import com.yapcore.plugin.PluginManager;
import com.yapcore.server.YaPcoreServer;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

/** Plugins tab — add / remove / refresh jars. */
public final class PluginsPanel {

    private static final Logger LOG = Logger.getLogger("YaPcore.GUI.Plugins");

    private final YaPcoreServer server;
    private final DefaultListModel<String> model = new DefaultListModel<>();
    private final JList<String> list = new JList<>(model);
    private final JPanel root;

    public PluginsPanel(YaPcoreServer server) {
        this.server = server;
        root = GuiTheme.card();
        root.setLayout(new BorderLayout(8, 8));
        root.add(GuiTheme.sectionTitle("Installed Plugins"), BorderLayout.NORTH);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setBackground(new java.awt.Color(0x0D, 0x11, 0x17));
        list.setForeground(GuiTheme.TEXT);
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        root.add(scroll, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.setOpaque(false);
        JButton add = new JButton("Add…");
        JButton remove = new JButton("Remove");
        JButton refresh = new JButton("Refresh");
        GuiTheme.stylePrimary(add);
        GuiTheme.styleDanger(remove);
        add.addActionListener(e -> addPlugin());
        remove.addActionListener(e -> removePlugin());
        refresh.addActionListener(e -> refresh());
        buttons.add(add);
        buttons.add(remove);
        buttons.add(refresh);
        root.add(buttons, BorderLayout.SOUTH);
        refresh();
    }

    public JPanel component() {
        return root;
    }

    public void refresh() {
        model.clear();
        List<PluginManager.PluginInfo> plugins = server.getPluginManager().listPlugins();
        for (PluginManager.PluginInfo p : plugins) {
            model.addElement(p.fileName() + " (" + p.sizeLabel() + ")");
        }
    }

    private void addPlugin() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select plugin JAR");
        chooser.setFileFilter(new FileNameExtensionFilter("Plugin jars (*.jar, *.yap)", "jar", "yap"));
        if (chooser.showOpenDialog(root) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path selected = chooser.getSelectedFile().toPath();
        try {
            PluginManager.PluginInfo info = server.getPluginManager().addPlugin(selected);
            LOG.info("Added plugin " + info.fileName());
            refresh();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(root, e.getMessage(), "Add Plugin", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void removePlugin() {
        String selected = list.getSelectedValue();
        if (selected == null) {
            JOptionPane.showMessageDialog(root, "Select a plugin first.", "Remove", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String fileName = selected.contains(" (") ? selected.substring(0, selected.indexOf(" (")) : selected;
        int confirm = JOptionPane.showConfirmDialog(root,
                "Remove plugin '" + fileName + "'?", "Confirm Remove", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            server.getPluginManager().removePlugin(fileName);
            refresh();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(root, e.getMessage(), "Remove Plugin", JOptionPane.ERROR_MESSAGE);
        }
    }
}
