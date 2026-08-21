package com.yapcore.gui.panels;

import com.yapcore.gui.theme.GuiTheme;
import com.yapcore.server.YaPcoreServer;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.logging.Logger;

/** Resource / texture packs tab. */
public final class PacksPanel {

    private static final Logger LOG = Logger.getLogger("YaPcore.GUI.Packs");

    private final YaPcoreServer server;
    private final DefaultListModel<String> model = new DefaultListModel<>();
    private final JList<String> list = new JList<>(model);
    private final JLabel activeLabel = new JLabel("none");
    private final JPanel root;

    public PacksPanel(YaPcoreServer server) {
        this.server = server;
        root = GuiTheme.card();
        root.setLayout(new BorderLayout(8, 8));
        root.add(GuiTheme.sectionTitle("Texture / Resource Packs"), BorderLayout.NORTH);

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setBackground(new java.awt.Color(0x0D, 0x11, 0x17));
        list.setForeground(GuiTheme.TEXT);
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        root.add(scroll, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.setOpaque(false);
        JButton add = new JButton("Add…");
        JButton activate = new JButton("Set Active");
        JButton remove = new JButton("Remove");
        JButton clear = new JButton("Clear Active");
        GuiTheme.stylePrimary(add);
        GuiTheme.stylePrimary(activate);
        GuiTheme.styleDanger(remove);
        add.addActionListener(e -> addPack());
        activate.addActionListener(e -> activatePack());
        remove.addActionListener(e -> removePack());
        clear.addActionListener(e -> clearPack());
        buttons.add(add);
        buttons.add(activate);
        buttons.add(remove);
        buttons.add(clear);
        root.add(buttons, BorderLayout.SOUTH);
        refresh();
    }

    public JPanel component() {
        return root;
    }

    public JLabel activeLabel() {
        return activeLabel;
    }

    public void refresh() {
        model.clear();
        String active = server.getResourcePacks().getActivePack()
                .map(p -> p.getFileName()).orElse("");
        for (var p : server.getResourcePacks().listPacks()) {
            String mark = p.getFileName().equals(active) ? " ★" : "";
            model.addElement(p.getFileName() + " (" + p.sizeLabel() + ")" + mark);
        }
        activeLabel.setText(active.isBlank() ? "none" : active);
        activeLabel.setForeground(GuiTheme.TEXT);
    }

    private void addPack() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select resource / texture pack");
        chooser.setFileFilter(new FileNameExtensionFilter("Packs (*.zip, *.mcpack)", "zip", "mcpack"));
        if (chooser.showOpenDialog(root) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            var info = server.getResourcePacks().addPack(chooser.getSelectedFile().toPath());
            LOG.info("Added pack " + info.getFileName());
            refresh();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(root, e.getMessage(), "Add Pack", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void activatePack() {
        String selected = list.getSelectedValue();
        if (selected == null) {
            JOptionPane.showMessageDialog(root, "Select a pack first.", "Set Active", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String fileName = selected.contains(" ") ? selected.substring(0, selected.indexOf(' ')) : selected;
        try {
            server.getResourcePacks().setActivePack(fileName);
            refresh();
            JOptionPane.showMessageDialog(root,
                    "Clients will download:\n" + server.getResourcePacks().buildPublicUrl(fileName),
                    "Active Pack", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(root, e.getMessage(), "Set Active", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void removePack() {
        String selected = list.getSelectedValue();
        if (selected == null) {
            return;
        }
        String fileName = selected.contains(" ") ? selected.substring(0, selected.indexOf(' ')) : selected;
        try {
            server.getResourcePacks().removePack(fileName);
            refresh();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(root, e.getMessage(), "Remove Pack", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void clearPack() {
        try {
            server.getResourcePacks().setActivePack("");
            refresh();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(root, e.getMessage(), "Clear Pack", JOptionPane.ERROR_MESSAGE);
        }
    }
}
