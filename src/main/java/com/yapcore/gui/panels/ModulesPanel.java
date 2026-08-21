package com.yapcore.gui.panels;

import com.yapcore.gui.theme.GuiTheme;
import com.yapcore.module.ModuleManager;
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

/** Modules tab — fine-tune jars in modules/ (module.yml). */
public final class ModulesPanel {

    private static final Logger LOG = Logger.getLogger("YaPcore.GUI.Modules");

    private final YaPcoreServer server;
    private final DefaultListModel<String> model = new DefaultListModel<>();
    private final JList<String> list = new JList<>(model);
    private final JPanel root;

    public ModulesPanel(YaPcoreServer server) {
        this.server = server;
        root = GuiTheme.card();
        root.setLayout(new BorderLayout(8, 8));
        root.add(GuiTheme.sectionTitle("Installed Modules (fine-tune)"), BorderLayout.NORTH);
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
        add.addActionListener(e -> addModule());
        remove.addActionListener(e -> removeModule());
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
        List<ModuleManager.ModuleInfo> modules = server.getModuleManager().listModules();
        for (ModuleManager.ModuleInfo m : modules) {
            model.addElement(m.fileName() + " (" + m.sizeLabel() + ")");
        }
    }

    private void addModule() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select module JAR");
        chooser.setFileFilter(new FileNameExtensionFilter("Modules (*.jar, *.yapmod)", "jar", "yapmod"));
        if (chooser.showOpenDialog(root) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path src = chooser.getSelectedFile().toPath();
        try {
            server.getModuleManager().addModule(src);
            refresh();
            JOptionPane.showMessageDialog(root,
                    "Module copied. Restart/start the server to load it.",
                    "Module added", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            LOG.warning("Add module failed: " + ex.getMessage());
            JOptionPane.showMessageDialog(root, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void removeModule() {
        String sel = list.getSelectedValue();
        if (sel == null) {
            return;
        }
        String file = sel.contains(" (") ? sel.substring(0, sel.indexOf(" (")) : sel;
        try {
            server.getModuleManager().removeModule(file);
            refresh();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(root, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
