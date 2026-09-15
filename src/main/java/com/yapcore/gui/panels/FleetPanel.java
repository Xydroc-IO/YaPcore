package com.yapcore.gui.panels;

import com.yapcore.gui.panels.fleet.DatabaseSetupDialog;
import com.yapcore.gui.panels.fleet.FleetBootstrapDialog;
import com.yapcore.gui.panels.fleet.FleetInstanceDialog;
import com.yapcore.gui.panels.fleet.FleetNodeDialog;
import com.yapcore.gui.panels.fleet.FleetPluginsDialog;
import com.yapcore.gui.theme.GuiTheme;
import com.yapcore.server.YaPcoreServer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;

/**
 * Fleet home — every game server with Start / Stop / Restart on the card.
 */
public final class FleetPanel {

    private final YaPcoreServer server;
    private final JPanel root = new JPanel(new BorderLayout(8, 8));
    private final JLabel status = new JLabel("Fleet disabled");
    private final JPanel cards = new JPanel(new GridLayout(0, 1, 8, 8));
    private final JComboBox<String> nodeFilter = new JComboBox<>(new String[]{"all", "local"});
    private final JTextArea console = new JTextArea(6, 40);
    private final JTextField command = new JTextField();
    private String selectedId;
    private Timer timer;
    private Runnable openLinkAction = () -> {
    };

    public FleetPanel(YaPcoreServer server) {
        this.server = server;
        root.setOpaque(false);
        root.setBorder(new EmptyBorder(2, 2, 2, 2));
        root.add(buildTop(), BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(cards);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(GuiTheme.BG);
        cards.setOpaque(false);
        root.add(scroll, BorderLayout.CENTER);
        root.add(buildBottom(), BorderLayout.SOUTH);
        timer = new Timer(2000, e -> refresh());
        timer.start();
        refresh();
    }

    public JPanel component() {
        return root;
    }

    /** Wired by ControlFleetShell to open the YaP Link workspace. */
    public void setOpenLinkAction(Runnable action) {
        this.openLinkAction = action == null ? () -> {
        } : action;
    }

    public void shutdown() {
        if (timer != null) {
            timer.stop();
        }
    }

    public void refreshNow() {
        refresh();
    }

    private JPanel buildTop() {
        JPanel panel = GuiTheme.card();
        panel.setLayout(new BorderLayout(6, 6));
        panel.add(GuiTheme.sectionTitle("Network servers"), BorderLayout.NORTH);
        status.setForeground(GuiTheme.MUTED);
        status.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        panel.add(status, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        actions.setOpaque(false);
        JButton openLink = btn("Open YaP Link…", () -> openLinkAction.run());
        GuiTheme.stylePrimary(openLink);
        actions.add(openLink);
        actions.add(btn("Enable fleet", this::enableFleet));
        actions.add(btn("Add server…", this::createInstance));
        actions.add(btn("Bootstrap…", this::bootstrap));
        actions.add(btn("Database…", this::databaseSetup));
        actions.add(btn("Sync Link", this::syncLink));
        actions.add(btn("Node…", this::addNode));
        actions.add(new JLabel("Node"));
        actions.add(nodeFilter);
        nodeFilter.addActionListener(e -> refresh());
        panel.add(actions, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildBottom() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setOpaque(false);
        console.setEditable(false);
        console.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        console.setBackground(new Color(0x0D, 0x11, 0x17));
        console.setForeground(GuiTheme.TEXT);
        panel.add(new JScrollPane(console), BorderLayout.CENTER);
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setOpaque(false);
        JButton send = new JButton("Send");
        GuiTheme.stylePrimary(send);
        send.addActionListener(e -> sendCommand());
        command.addActionListener(e -> sendCommand());
        row.add(command, BorderLayout.CENTER);
        row.add(send, BorderLayout.EAST);
        panel.add(row, BorderLayout.SOUTH);
        return panel;
    }

    private void refresh() {
        try {
            Map<String, Object> snap = server.fleet().statusSnapshot();
            boolean enabled = Boolean.TRUE.equals(snap.get("fleetEnabled"));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> instances = (List<Map<String, Object>>) snap.get("instances");
            int n = instances == null ? 0 : instances.size();
            status.setText(enabled
                    ? ("Fleet · " + n + " server(s) · primary=" + snap.get("primaryId")
                    + " — Start / Stop / Restart each below")
                    : "Fleet disabled — Enable fleet to manage lobby, survival, …");
            cards.removeAll();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> health = (List<Map<String, Object>>) snap.get("backendHealth");
            Object sel = nodeFilter.getSelectedItem();
            String filter = (sel == null || "null".equals(String.valueOf(sel)))
                    ? "all"
                    : String.valueOf(sel);
            int shown = 0;
            if (instances != null) {
                for (Map<String, Object> i : instances) {
                    String nodeId = String.valueOf(i.get("nodeId"));
                    if (!"all".equals(filter) && !filter.equals(nodeId)) {
                        continue;
                    }
                    cards.add(instanceCard(i, health));
                    shown++;
                }
            }
            if (enabled && shown == 0) {
                JLabel empty = new JLabel(n == 0
                        ? "No servers yet — Add server or Bootstrap"
                        : "No servers match node filter \"" + filter + "\" — set filter to all");
                empty.setForeground(GuiTheme.MUTED);
                cards.add(empty);
            }
            // Rebuild filter without re-entering refresh via ActionListener
            java.awt.event.ActionListener[] listeners = nodeFilter.getActionListeners();
            for (java.awt.event.ActionListener l : listeners) {
                nodeFilter.removeActionListener(l);
            }
            try {
                nodeFilter.removeAllItems();
                nodeFilter.addItem("all");
                nodeFilter.addItem("local");
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> nodes = (List<Map<String, Object>>) snap.get("nodes");
                if (nodes != null) {
                    for (Map<String, Object> node : nodes) {
                        nodeFilter.addItem(String.valueOf(node.get("id")));
                    }
                }
                boolean restored = false;
                for (int i = 0; i < nodeFilter.getItemCount(); i++) {
                    if (filter.equals(nodeFilter.getItemAt(i))) {
                        nodeFilter.setSelectedIndex(i);
                        restored = true;
                        break;
                    }
                }
                if (!restored) {
                    nodeFilter.setSelectedItem("all");
                }
            } finally {
                for (java.awt.event.ActionListener l : listeners) {
                    nodeFilter.addActionListener(l);
                }
            }
            cards.revalidate();
            cards.repaint();
            root.revalidate();
            root.repaint();
        } catch (Exception e) {
            status.setText("Fleet error: " + e.getMessage());
            cards.removeAll();
            JLabel err = new JLabel("Error loading fleet: " + e.getMessage());
            err.setForeground(new Color(0xE3, 0x6B, 0x6B));
            cards.add(err);
            cards.revalidate();
            cards.repaint();
        }
    }

    private JPanel instanceCard(Map<String, Object> i, List<Map<String, Object>> health) {
        String id = String.valueOf(i.get("id"));
        String name = String.valueOf(i.getOrDefault("displayName", id));
        String state = String.valueOf(i.getOrDefault("state", "?"));
        boolean running = Boolean.TRUE.equals(i.get("running")) || "RUNNING".equalsIgnoreCase(state);
        String online = healthLine(String.valueOf(i.get("serverId")), health);

        JPanel card = GuiTheme.card();
        card.setLayout(new BorderLayout(8, 6));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0x2A, 0x35, 0x40)),
                new EmptyBorder(10, 12, 10, 12)));

        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        JLabel title = new JLabel(name + "  (" + id + ")");
        title.setFont(new Font("Segoe UI", Font.BOLD, 14));
        title.setForeground(GuiTheme.TEXT);
        JLabel badge = new JLabel(running ? "● RUNNING" : "○ " + state);
        badge.setForeground(running ? GuiTheme.ACCENT : new Color(0xE3, 0x6B, 0x6B));
        badge.setFont(new Font("Segoe UI", Font.BOLD, 11));
        head.add(title, BorderLayout.WEST);
        head.add(badge, BorderLayout.EAST);
        card.add(head, BorderLayout.NORTH);

        Object pc = i.get("pluginCount");
        String pluginLabel = pc == null ? "plugins ?" : (pc + " plugins");
        JLabel meta = new JLabel("Port " + i.get("port") + " · " + pluginLabel
                + " · Node " + i.get("nodeId") + " · " + online);
        meta.setForeground(GuiTheme.MUTED);
        meta.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        card.add(meta, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        actions.setOpaque(false);
        JButton plugins = btn("Plugins…", () -> openPlugins(id));
        GuiTheme.stylePrimary(plugins);
        actions.add(plugins);
        actions.add(btn("Start", () -> act(id, "start")));
        actions.add(btn("Stop", () -> act(id, "stop")));
        actions.add(btn("Restart", () -> act(id, "restart")));
        actions.add(btn("Setup…", () -> editSettings(id)));
        actions.add(btn("Console", () -> {
            selectedId = id;
            command.requestFocusInWindow();
        }));
        if (pc instanceof Number && ((Number) pc).intValue() == 0) {
            actions.add(btn("Install defaults", () -> installDefaults(id)));
        }
        JButton del = btn("Delete", () -> act(id, "delete"));
        GuiTheme.styleDanger(del);
        actions.add(del);
        card.add(actions, BorderLayout.SOUTH);
        return card;
    }

    private static String healthLine(String serverId, List<Map<String, Object>> health) {
        if (health == null) {
            return "health —";
        }
        for (Map<String, Object> h : health) {
            if (serverId.equalsIgnoreCase(String.valueOf(h.get("name")))) {
                return (Boolean.TRUE.equals(h.get("up")) ? "up " : "down ")
                        + h.get("online") + "/" + h.get("max")
                        + " " + h.get("latencyMs") + "ms";
            }
        }
        return "health —";
    }

    private void enableFleet() {
        run(() -> server.fleet().enableFleet());
    }

    private void createInstance() {
        java.awt.Frame frame = (java.awt.Frame) javax.swing.SwingUtilities.getWindowAncestor(root);
        FleetInstanceDialog d = new FleetInstanceDialog(frame, server);
        d.setVisible(true);
        if (!d.accepted()) {
            return;
        }
        String newId = d.id();
        List<String> jars = d.selectedPlugins();
        int maxPlayers = d.maxPlayers();
        int ramMb = d.ramMb();
        String motd = d.motd();
        run(() -> {
            server.fleet().createInstance(newId, d.displayName(), d.port(), d.autoStart(), ramMb, null);
            java.util.HashMap<String, String> settings = new java.util.HashMap<>();
            settings.put("max-players", Integer.toString(maxPlayers));
            if (motd != null && !motd.isBlank()) {
                settings.put("motd", motd);
            }
            server.fleet().writeInstanceSettings(newId, settings);
            for (String jar : jars) {
                server.fleet().installCatalogJar(jar, List.of(newId), "none");
            }
            return Map.of("ok", true, "id", newId, "plugins", jars.size());
        });
    }

    private void openPlugins(String id) {
        java.awt.Frame frame = (java.awt.Frame) javax.swing.SwingUtilities.getWindowAncestor(root);
        new FleetPluginsDialog(frame, server, id).setVisible(true);
        refresh();
    }

    private void installDefaults(String id) {
        run(() -> server.fleet().installCoreNetworkDefaults(id));
    }

    private void addNode() {
        java.awt.Frame frame = (java.awt.Frame) javax.swing.SwingUtilities.getWindowAncestor(root);
        FleetNodeDialog d = new FleetNodeDialog(frame, server.getRootDir());
        d.setVisible(true);
        if (!d.accepted()) {
            return;
        }
        run(() -> server.fleet().putNode(d.node()));
    }

    private void bootstrap() {
        java.awt.Frame frame = (java.awt.Frame) javax.swing.SwingUtilities.getWindowAncestor(root);
        FleetBootstrapDialog d = new FleetBootstrapDialog(frame);
        d.setVisible(true);
        if (!d.accepted()) {
            return;
        }
        run(() -> server.fleet().bootstrap(
                d.jdbcUrl(), d.createSurvival(), d.enableVelocity(), d.startInstances()));
    }

    private void databaseSetup() {
        java.awt.Frame frame = (java.awt.Frame) javax.swing.SwingUtilities.getWindowAncestor(root);
        DatabaseSetupDialog d = new DatabaseSetupDialog(frame, server.getRootDir());
        d.setVisible(true);
    }

    private void syncLink() {
        run(() -> server.fleet().syncLink());
    }

    private void act(String id, String action) {
        if ("delete".equals(action)
                && JOptionPane.showConfirmDialog(root, "Delete " + id + "?", "Delete",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        selectedId = id;
        run(() -> {
            switch (action) {
                case "start" -> server.fleet().startInstance(id);
                case "stop" -> server.fleet().stopInstance(id);
                case "restart" -> server.fleet().restartInstance(id);
                case "delete" -> server.fleet().deleteInstance(id);
                default -> {
                }
            }
            return Map.of("ok", true);
        });
    }

    private void editSettings(String id) {
        selectedId = id;
        run(() -> {
            Map<String, Object> cur = server.fleet().readInstanceSettings(id);
            @SuppressWarnings("unchecked")
            Map<String, String> props = (Map<String, String>) cur.get("properties");
            @SuppressWarnings("unchecked")
            Map<String, Object> inst = (Map<String, Object>) cur.get("instance");
            String motd = props == null ? "" : String.valueOf(props.getOrDefault("motd", ""));
            String max = props == null ? "" : String.valueOf(props.getOrDefault("max-players", ""));
            String port = inst == null ? "" : String.valueOf(inst.getOrDefault("port", ""));
            javax.swing.JTextField motdField = new javax.swing.JTextField(motd, 24);
            javax.swing.JTextField maxField = new javax.swing.JTextField(max, 8);
            javax.swing.JTextField portField = new javax.swing.JTextField(port, 8);
            JPanel form = new JPanel(new GridLayout(0, 1, 4, 4));
            form.add(new JLabel("MOTD"));
            form.add(motdField);
            form.add(new JLabel("Max players"));
            form.add(maxField);
            form.add(new JLabel("Port"));
            form.add(portField);
            int ok = JOptionPane.showConfirmDialog(root, form, "Setup · " + id, JOptionPane.OK_CANCEL_OPTION);
            if (ok != JOptionPane.OK_OPTION) {
                return Map.of("ok", false);
            }
            java.util.HashMap<String, String> body = new java.util.HashMap<>();
            body.put("motd", motdField.getText().trim());
            body.put("max-players", maxField.getText().trim());
            body.put("port", portField.getText().trim());
            return server.fleet().writeInstanceSettings(id, body);
        });
    }

    private void sendCommand() {
        String id = selectedId;
        if (id == null) {
            JOptionPane.showMessageDialog(root, "Click Console on a server card first");
            return;
        }
        String line = command.getText();
        command.setText("");
        run(() -> {
            String result = server.fleet().dispatch(id, line);
            console.append("[" + id + "] " + result + "\n");
            return Map.of("ok", true);
        });
    }

    private void run(Worker work) {
        new SwingWorker<Map<String, Object>, Void>() {
            @Override
            protected Map<String, Object> doInBackground() throws Exception {
                return work.run();
            }

            @Override
            protected void done() {
                try {
                    get();
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(root, e.getMessage(), "Fleet", JOptionPane.ERROR_MESSAGE);
                }
                refresh();
            }
        }.execute();
    }

    private static JButton btn(String label, Runnable action) {
        JButton b = new JButton(label);
        b.addActionListener(e -> action.run());
        return b;
    }

    @FunctionalInterface
    private interface Worker {
        Map<String, Object> run() throws Exception;
    }
}
