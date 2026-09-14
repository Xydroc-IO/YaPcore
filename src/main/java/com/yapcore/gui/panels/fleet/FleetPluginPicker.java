package com.yapcore.gui.panels.fleet;

import com.yapcore.fleet.local.FleetDefaultPlugins;
import com.yapcore.plugin.PluginManager;
import com.yapcore.server.YaPcoreServer;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

/** Scrollable checklist of catalog jars under root {@code plugins/}. */
public final class FleetPluginPicker extends JPanel {

    private final Map<String, JCheckBox> boxes = new LinkedHashMap<>();

    public FleetPluginPicker(YaPcoreServer server) {
        this(server, true);
    }

    public FleetPluginPicker(YaPcoreServer server, boolean selectCoreByDefault) {
        setLayout(new BorderLayout(4, 4));
        setOpaque(false);
        JLabel hint = new JLabel("Select plugins from catalog (root plugins/)");
        hint.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        add(hint, BorderLayout.NORTH);
        JPanel list = new JPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setOpaque(false);
        for (PluginManager.PluginInfo info : server.getPluginManager().listPlugins()) {
            String name = info.fileName();
            boolean core = isCoreDefault(name);
            JCheckBox box = new JCheckBox(name + "  (" + info.sizeLabel() + ")",
                    selectCoreByDefault && core);
            boxes.put(name, box);
            list.add(box);
        }
        if (boxes.isEmpty()) {
            list.add(new JLabel("No jars in plugins/ — Upload into catalog first"));
        }
        JScrollPane scroll = new JScrollPane(list);
        scroll.setPreferredSize(new Dimension(420, 220));
        scroll.getViewport().setOpaque(false);
        add(scroll, BorderLayout.CENTER);
        JPanel bulk = new JPanel();
        bulk.setOpaque(false);
        javax.swing.JButton all = new javax.swing.JButton("Select all");
        javax.swing.JButton none = new javax.swing.JButton("Select none");
        javax.swing.JButton core = new javax.swing.JButton("Core suite");
        all.addActionListener(e -> setAll(true));
        none.addActionListener(e -> setAll(false));
        core.addActionListener(e -> selectCoreOnly());
        bulk.add(all);
        bulk.add(none);
        bulk.add(core);
        add(bulk, BorderLayout.SOUTH);
    }

    public List<String> selectedJars() {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, JCheckBox> e : boxes.entrySet()) {
            if (e.getValue().isSelected()) {
                out.add(e.getKey());
            }
        }
        return out;
    }

    public void setSelectedJars(Iterable<String> jars) {
        setAll(false);
        if (jars == null) {
            return;
        }
        for (String jar : jars) {
            JCheckBox box = boxes.get(Path.of(jar).getFileName().toString());
            if (box != null) {
                box.setSelected(true);
            }
        }
    }

    private void setAll(boolean on) {
        for (JCheckBox box : boxes.values()) {
            box.setSelected(on);
        }
    }

    private void selectCoreOnly() {
        for (Map.Entry<String, JCheckBox> e : boxes.entrySet()) {
            e.getValue().setSelected(FleetDefaultPlugins.isCoreDefault(e.getKey()));
        }
    }

    static boolean isCoreDefault(String fileName) {
        return FleetDefaultPlugins.isCoreDefault(fileName);
    }
}
