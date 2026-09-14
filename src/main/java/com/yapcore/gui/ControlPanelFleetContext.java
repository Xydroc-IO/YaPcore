package com.yapcore.gui;

import com.yapcore.fleet.service.FleetService;
import com.yapcore.server.YaPcoreServer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;

/** Shared Network | instance context for the Swing control shell. */
public final class ControlPanelFleetContext {

    public static final String NETWORK = "Network";

    private final YaPcoreServer server;
    private final DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
    private final JComboBox<String> combo = new JComboBox<>(model);
    private boolean refreshing;

    public ControlPanelFleetContext(YaPcoreServer server) {
        this.server = server;
        combo.setToolTipText("Network = Fleet home (all servers). Pick a server to manage plugins/setup.");
        refresh();
    }

    public JComboBox<String> combo() {
        return combo;
    }

    public boolean fleetEnabled() {
        return server.getConfig().isFleetEnabled();
    }

    public void refresh() {
        refreshing = true;
        try {
            String prev = selectedRaw();
            String prevId = instanceIdFrom(prev);
            model.removeAllElements();
            model.addElement(NETWORK);
            if (!fleetEnabled()) {
                combo.setSelectedIndex(0);
                return;
            }
            try {
                Map<String, Object> snap = server.fleet().statusSnapshot();
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> instances = (List<Map<String, Object>>) snap.get("instances");
                if (instances != null) {
                    for (Map<String, Object> i : instances) {
                        String id = String.valueOf(i.get("id"));
                        String name = String.valueOf(i.getOrDefault("displayName", id));
                        String state = String.valueOf(i.getOrDefault("state", "?"));
                        model.addElement(name + " [" + id + "] · " + state);
                    }
                }
            } catch (Exception ignored) {
                // keep Network only
            }
            int select = 0;
            if (prevId != null) {
                for (int i = 0; i < model.getSize(); i++) {
                    if (prevId.equals(instanceIdFrom(model.getElementAt(i)))) {
                        select = i;
                        break;
                    }
                }
            } else if (NETWORK.equals(prev)) {
                select = 0;
            }
            combo.setSelectedIndex(Math.min(select, Math.max(0, model.getSize() - 1)));
        } finally {
            refreshing = false;
        }
    }

    public boolean isRefreshing() {
        return refreshing;
    }

    public boolean isNetwork() {
        return NETWORK.equals(selectedRaw()) || instanceId() == null;
    }

    public String instanceId() {
        return instanceIdFrom(selectedRaw());
    }

    public String headerSummary() {
        if (!fleetEnabled()) {
            int port = server.getConfig().getPort();
            return "This PC: 127.0.0.1:" + port + " · single-server mode";
        }
        try {
            Map<String, Object> snap = server.fleet().statusSnapshot();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> instances = (List<Map<String, Object>>) snap.get("instances");
            List<String> parts = new ArrayList<>();
            parts.add("Link :" + new com.yapcore.network.publicity.PublicEndpoint(server.getConfig())
                    .advertisedJavaPort());
            if (instances != null) {
                for (Map<String, Object> i : instances) {
                    parts.add(i.get("id") + ":" + i.get("port")
                            + " " + i.getOrDefault("state", "?"));
                }
            }
            return String.join("  ·  ", parts);
        } catch (Exception e) {
            return "Fleet · " + e.getMessage();
        }
    }

    public void startSelected() throws Exception {
        FleetService fleet = server.fleet();
        if (!fleetEnabled()) {
            server.start();
            return;
        }
        // Fleet Folia JVMs don't start DualStack — still need :8081 for YaPItems models.
        server.ensurePackHttp();
        String id = instanceId();
        if (id == null) {
            String primary = fleet.store().primaryId();
            if (primary == null || primary.isBlank()) {
                primary = "lobby";
            }
            fleet.startInstance(primary);
            return;
        }
        fleet.startInstance(id);
    }

    public void stopSelected() throws Exception {
        FleetService fleet = server.fleet();
        if (!fleetEnabled()) {
            server.stop();
            return;
        }
        String id = instanceId();
        if (id == null) {
            fleet.stopAllLocal();
            return;
        }
        fleet.stopInstance(id);
    }

    public void restartSelected() throws Exception {
        if (!fleetEnabled()) {
            server.stop();
            server.start();
            return;
        }
        String id = instanceId();
        if (id == null) {
            String primary = server.fleet().store().primaryId();
            if (primary == null || primary.isBlank()) {
                primary = "lobby";
            }
            server.fleet().restartInstance(primary);
            return;
        }
        server.fleet().restartInstance(id);
    }

    public String dispatchCommand(String line) {
        if (fleetEnabled() && instanceId() != null) {
            try {
                return server.fleet().dispatch(instanceId(), line);
            } catch (Exception e) {
                return "Error: " + e.getMessage();
            }
        }
        return server.executeCommand(line);
    }

    public String startButtonLabel() {
        if (!fleetEnabled()) {
            return "Start Server";
        }
        String id = instanceId();
        return id == null ? "Start primary" : "Start " + id;
    }

    public String stopButtonLabel() {
        if (!fleetEnabled()) {
            return "Stop Server";
        }
        String id = instanceId();
        return id == null ? "Stop all" : "Stop " + id;
    }

    private String selectedRaw() {
        Object v = combo.getSelectedItem();
        return v == null ? NETWORK : String.valueOf(v);
    }

    static String instanceIdFrom(String label) {
        if (label == null || NETWORK.equals(label)) {
            return null;
        }
        int open = label.indexOf('[');
        int close = label.indexOf(']');
        if (open >= 0 && close > open) {
            return label.substring(open + 1, close).trim();
        }
        return null;
    }
}
