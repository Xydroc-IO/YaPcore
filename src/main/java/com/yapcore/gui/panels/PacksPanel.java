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
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/** Resource / texture packs tab — multiple packs can be active at once. */
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
        root.add(GuiTheme.sectionTitle("Texture / Resource Packs (multi-active)"), BorderLayout.NORTH);

        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        list.setBackground(new java.awt.Color(0x0D, 0x11, 0x17));
        list.setForeground(GuiTheme.TEXT);
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        root.add(scroll, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.setOpaque(false);
        JButton add = new JButton("Add…");
        JButton activate = new JButton("Add to Active");
        JButton deactivate = new JButton("Remove from Active");
        JButton only = new JButton("Set Only Active");
        JButton remove = new JButton("Delete File");
        JButton clear = new JButton("Clear All Active");
        GuiTheme.stylePrimary(add);
        GuiTheme.stylePrimary(activate);
        GuiTheme.stylePrimary(deactivate);
        GuiTheme.stylePrimary(only);
        GuiTheme.styleDanger(remove);
        add.addActionListener(e -> addPack());
        activate.addActionListener(e -> addToActive());
        deactivate.addActionListener(e -> removeFromActive());
        only.addActionListener(e -> setOnlyActive());
        remove.addActionListener(e -> removePack());
        clear.addActionListener(e -> clearPack());
        buttons.add(add);
        buttons.add(activate);
        buttons.add(deactivate);
        buttons.add(only);
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
        var actives = server.getResourcePacks().getActivePacks().stream()
                .map(p -> p.getFileName()).toList();
        for (var p : server.getResourcePacks().listPacks()) {
            String mark = actives.contains(p.getFileName()) ? " ★" : "";
            model.addElement(p.getFileName() + " (" + p.sizeLabel() + ")" + mark);
        }
        activeLabel.setText(actives.isEmpty() ? "none" : String.join(", ", actives));
        activeLabel.setForeground(GuiTheme.TEXT);
    }

    private List<String> selectedFileNames() {
        List<String> out = new ArrayList<>();
        for (String selected : list.getSelectedValuesList()) {
            out.add(selected.contains(" ") ? selected.substring(0, selected.indexOf(' ')) : selected);
        }
        return out;
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

    private void addToActive() {
        List<String> names = selectedFileNames();
        if (names.isEmpty()) {
            JOptionPane.showMessageDialog(root, "Select one or more packs.", "Add to Active",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            for (String n : names) {
                server.getResourcePacks().addActivePack(n);
            }
            refresh();
            JOptionPane.showMessageDialog(root,
                    "Active packs:\n" + String.join("\n",
                            server.getResourcePacks().getActivePacks().stream()
                                    .map(p -> p.getFileName() + " → "
                                            + server.getResourcePacks().buildPublicUrl(p.getFileName()))
                                    .toList()),
                    "Active Packs", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(root, e.getMessage(), "Add to Active", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void removeFromActive() {
        List<String> names = selectedFileNames();
        if (names.isEmpty()) {
            return;
        }
        try {
            for (String n : names) {
                server.getResourcePacks().removeActivePack(n);
            }
            refresh();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(root, e.getMessage(), "Remove from Active", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void setOnlyActive() {
        List<String> names = selectedFileNames();
        if (names.isEmpty()) {
            JOptionPane.showMessageDialog(root, "Select one or more packs.", "Set Only Active",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            server.getResourcePacks().setActivePacks(names);
            refresh();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(root, e.getMessage(), "Set Only Active", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void removePack() {
        List<String> names = selectedFileNames();
        if (names.isEmpty()) {
            return;
        }
        try {
            for (String n : names) {
                server.getResourcePacks().removePack(n);
            }
            refresh();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(root, e.getMessage(), "Delete File", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void clearPack() {
        try {
            server.getResourcePacks().setActivePacks(List.of());
            refresh();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(root, e.getMessage(), "Clear All Active", JOptionPane.ERROR_MESSAGE);
        }
    }
}
