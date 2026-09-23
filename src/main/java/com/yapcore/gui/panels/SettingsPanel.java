package com.yapcore.gui.panels;

import com.yapcore.config.ServerConfig;
import com.yapcore.gui.theme.GuiTheme;
import com.yapcore.server.YaPcoreServer;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.function.Consumer;

/**
 * General server settings — identity, listen ports, editions, limits.
 */
public final class SettingsPanel {

    private final YaPcoreServer server;
    private final JPanel root = new JPanel(new BorderLayout());
    private String fleetInstanceId;
    private final JTextField nameField = new JTextField();
    private final JTextField motdField = new JTextField();
    private final JTextField bindField = new JTextField();
    private final JSpinner javaPortSpinner;
    private final JSpinner bedrockPortSpinner;
    private final JSpinner packPortSpinner;
    private final JSpinner maxPlayersSpinner;
    private final JSpinner ramMaxSpinner;
    private final JSpinner ramMinSpinner;
    private final JSpinner viewDistanceSpinner;
    private final JCheckBox javaBox = new JCheckBox("Allow Java Edition");
    private final JCheckBox bedrockBox = new JCheckBox("Allow Bedrock Edition (via YaP Link)");
    private final JCheckBox sharedPortBox = new JCheckBox("Chassis shared listen port (forwarder only)");
    private final JCheckBox crossplayBox = new JCheckBox("Shared world (Java + Bedrock same Folia world)");
    private final JCheckBox allowLocalBox = new JCheckBox("Allow same-PC clients (127.0.0.1 / localhost)");
    private final JCheckBox backwardsBox = new JCheckBox(
            "Built-in multi-version (accept all registered JE/BE protocols)");
    private final JCheckBox onlineModeBox = new JCheckBox("Online mode (auth)");
    private final JCheckBox packsBox = new JCheckBox("Resource pack HTTP server");
    private Consumer<Void> onSaved = v -> {
    };

    public SettingsPanel(YaPcoreServer server) {
        this.server = server;
        ServerConfig cfg = server.getConfig();
        javaPortSpinner = new JSpinner(new SpinnerNumberModel(cfg.getPort(), 1, 65_535, 1));
        bedrockPortSpinner = new JSpinner(new SpinnerNumberModel(cfg.getBedrockPort(), 1, 65_535, 1));
        packPortSpinner = new JSpinner(new SpinnerNumberModel(cfg.getResourcePackHttpPort(), 1, 65_535, 1));
        maxPlayersSpinner = new JSpinner(new SpinnerNumberModel(cfg.getMaxPlayers(), 1, 10_000, 1));
        ramMaxSpinner = new JSpinner(new SpinnerNumberModel(cfg.getRamMb(), 256, 131_072, 256));
        ramMinSpinner = new JSpinner(new SpinnerNumberModel(cfg.getRamMinMb(), 128, 65_536, 128));
        viewDistanceSpinner = new JSpinner(new SpinnerNumberModel(cfg.getViewDistance(), 2, 32, 1));

        root.setOpaque(false);
        root.setBorder(new EmptyBorder(2, 2, 2, 2));
        root.add(GuiTheme.verticalScroll(buildForm()), BorderLayout.CENTER);
        loadFromConfig();
    }

    public JPanel component() {
        return root;
    }

    /**
     * Scope identity / limits to a fleet game server. When set, the form loads that instance's
     * display name + server.properties (not the shared chassis name).
     */
    public void setFleetInstance(String instanceId) {
        String next = instanceId == null || instanceId.isBlank() ? null : instanceId.trim();
        boolean changed = !java.util.Objects.equals(this.fleetInstanceId, next);
        this.fleetInstanceId = next;
        if (changed) {
            loadFromConfig();
        }
    }

    public void setOnSaved(Consumer<Void> onSaved) {
        this.onSaved = onSaved != null ? onSaved : v -> {
        };
    }

    public void reloadFromConfig() {
        loadFromConfig();
    }

    private JPanel buildForm() {
        JPanel panel = GuiTheme.card();
        panel.setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.insets = new Insets(4, 4, 4, 4);

        panel.add(GuiTheme.sectionTitle("Settings"), c);
        c.gridy++;
        panel.add(GuiTheme.tip("Check <b>both</b> Java and Bedrock so both editions can join. "
                + "Bedrock UDP is owned by <b>YaP Link</b> (not this chassis checkbox from older builds). "
                + "Domain / ports for players: <b>Access</b> · join addresses: <b>YaP Link → Connect</b>."), c);

        c.gridy++;
        panel.add(GuiTheme.sectionTitle("Identity"), c);
        c.gridy++;
        panel.add(labeled("Server name", nameField), c);
        c.gridy++;
        panel.add(labeled("MOTD", motdField), c);

        c.gridy++;
        panel.add(GuiTheme.sectionTitle("Listen"), c);
        c.gridy++;
        panel.add(labeled("Bind host (0.0.0.0 = all interfaces)", bindField), c);
        c.gridy++;
        panel.add(labeled("Java TCP port", javaPortSpinner), c);
        c.gridy++;
        panel.add(labeled("Bedrock UDP port", bedrockPortSpinner), c);
        c.gridy++;
        panel.add(labeled("Resource pack HTTP port", packPortSpinner), c);

        c.gridy++;
        panel.add(GuiTheme.sectionTitle("Editions & compat"), c);
        c.gridy++;
        styleCheck(javaBox);
        panel.add(javaBox, c);
        c.gridy++;
        styleCheck(bedrockBox);
        panel.add(bedrockBox, c);
        c.gridy++;
        styleCheck(sharedPortBox);
        panel.add(sharedPortBox, c);
        c.gridy++;
        styleCheck(crossplayBox);
        panel.add(crossplayBox, c);
        c.gridy++;
        styleCheck(allowLocalBox);
        panel.add(allowLocalBox, c);
        c.gridy++;
        styleCheck(backwardsBox);
        panel.add(backwardsBox, c);
        c.gridy++;
        styleCheck(onlineModeBox);
        panel.add(onlineModeBox, c);
        c.gridy++;
        styleCheck(packsBox);
        panel.add(packsBox, c);

        c.gridy++;
        panel.add(GuiTheme.sectionTitle("Limits"), c);
        c.gridy++;
        panel.add(labeled("Max players", maxPlayersSpinner), c);
        c.gridy++;
        panel.add(labeled("RAM max MB (-Xmx, needs restart)", ramMaxSpinner), c);
        c.gridy++;
        panel.add(labeled("RAM min MB (-Xms, needs restart)", ramMinSpinner), c);
        c.gridy++;
        panel.add(labeled("View distance", viewDistanceSpinner), c);

        c.gridy++;
        JButton save = new JButton("Save settings");
        GuiTheme.stylePrimary(save);
        save.addActionListener(e -> save());
        panel.add(save, c);
        c.gridy++;
        c.weighty = 1;
        panel.add(new JLabel(), c);
        return panel;
    }

    private void loadFromConfig() {
        ServerConfig cfg = server.getConfig();
        nameField.setText(cfg.getServerName());
        motdField.setText(cfg.getMotd());
        bindField.setText(cfg.getBindHost());
        javaPortSpinner.setValue(cfg.getPort());
        bedrockPortSpinner.setValue(cfg.getBedrockPort());
        packPortSpinner.setValue(cfg.getResourcePackHttpPort());
        maxPlayersSpinner.setValue(cfg.getMaxPlayers());
        ramMaxSpinner.setValue(cfg.getRamMb());
        ramMinSpinner.setValue(cfg.getRamMinMb());
        viewDistanceSpinner.setValue(cfg.getViewDistance());
        javaBox.setSelected(cfg.isJavaEnabled());
        // Native mode: chassis bedrock-enabled is always "off" by design — Link owns UDP.
        // Reflect whether Bedrock players can actually join (Link edge).
        bedrockBox.setSelected(com.yapcore.web.DashboardLinkSnapshot.allowBedrockPlayers(
                server.getRootDir(), cfg.getLinkEmbedHome()));
        boolean forwarder = "forwarder".equals(cfg.getBedrockMode());
        sharedPortBox.setSelected(cfg.isSharedListenPort());
        sharedPortBox.setEnabled(forwarder);
        sharedPortBox.setToolTipText(forwarder
                ? "Bind chassis Java TCP + Bedrock UDP on the same port number."
                : "Not used in Link-native mode — Link already accepts JE TCP + BE UDP on the public edge.");
        crossplayBox.setSelected(cfg.isCrossplayEnabled());
        allowLocalBox.setSelected(cfg.isAllowLocalhost());
        backwardsBox.setSelected(cfg.isBackwardsCompatible());
        onlineModeBox.setSelected(cfg.isOnlineMode());
        packsBox.setSelected(cfg.isResourcePackEnabled());
        overlayFleetInstance(cfg);
    }

    /** Replace identity / limits with the selected fleet instance's values. */
    private void overlayFleetInstance(ServerConfig cfg) {
        if (fleetInstanceId == null || !cfg.isFleetEnabled()) {
            return;
        }
        try {
            java.util.Map<String, Object> snap = server.fleet().readInstanceSettings(fleetInstanceId);
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> inst = (java.util.Map<String, Object>) snap.get("instance");
            @SuppressWarnings("unchecked")
            java.util.Map<String, String> props = (java.util.Map<String, String>) snap.get("properties");
            if (inst != null) {
                Object dn = inst.get("displayName");
                nameField.setText(dn == null || String.valueOf(dn).isBlank()
                        ? fleetInstanceId : String.valueOf(dn));
                int ram = asPositiveInt(inst.get("ramMb"));
                int ramMin = asPositiveInt(inst.get("ramMinMb"));
                if (ram > 0) {
                    ramMaxSpinner.setValue(ram);
                }
                if (ramMin > 0) {
                    ramMinSpinner.setValue(ramMin);
                }
            }
            if (props != null) {
                if (props.containsKey("motd")) {
                    motdField.setText(props.get("motd"));
                }
                Integer max = parsePositive(props.get("max-players"));
                if (max != null) {
                    maxPlayersSpinner.setValue(max);
                }
                Integer view = parsePositive(props.get("view-distance"));
                if (view != null) {
                    viewDistanceSpinner.setValue(view);
                }
            }
        } catch (Exception ignored) {
            // keep chassis fallbacks already filled above
        }
    }

    private void save() {
        try {
            ServerConfig cfg = server.getConfig();
            int ramMax = (Integer) ramMaxSpinner.getValue();
            int ramMin = (Integer) ramMinSpinner.getValue();
            if (ramMin > ramMax) {
                JOptionPane.showMessageDialog(root, "RAM min cannot exceed RAM max.",
                        "Invalid", JOptionPane.WARNING_MESSAGE);
                return;
            }
            boolean fleetScoped = fleetInstanceId != null && cfg.isFleetEnabled();
            // Identity + limits are per game server when a fleet instance is selected —
            // do not clobber the shared chassis name / MOTD / heap with one sibling's values.
            if (!fleetScoped) {
                cfg.setServerName(nameField.getText().trim());
                cfg.setMotd(motdField.getText());
                cfg.setMaxPlayers((Integer) maxPlayersSpinner.getValue());
                cfg.setRamMb(ramMax);
                cfg.setRamMinMb(ramMin);
                cfg.setViewDistance((Integer) viewDistanceSpinner.getValue());
            }
            cfg.setBindHost(bindField.getText().trim());
            cfg.setPort((Integer) javaPortSpinner.getValue());
            cfg.setBedrockPort((Integer) bedrockPortSpinner.getValue());
            cfg.setResourcePackHttpPort((Integer) packPortSpinner.getValue());
            cfg.setJavaEnabled(javaBox.isSelected());
            boolean wantBedrock = bedrockBox.isSelected();
            boolean wantJava = javaBox.isSelected();
            if (!wantJava && !wantBedrock) {
                JOptionPane.showMessageDialog(root,
                        "Leave at least one of Java or Bedrock enabled so someone can join.",
                        "Invalid", JOptionPane.WARNING_MESSAGE);
                return;
            }
            // Chassis Folia must not bind Bedrock UDP in native/geyser-backup — Link (or Geyser) owns it.
            String mode = cfg.getBedrockMode();
            boolean forwarder = "forwarder".equals(mode);
            if (forwarder) {
                cfg.setBedrockEnabled(wantBedrock);
                cfg.setSharedListenPort(sharedPortBox.isSelected());
                if (sharedPortBox.isSelected()) {
                    cfg.setBedrockPort(cfg.getPort());
                }
            } else {
                cfg.setBedrockEnabled(false);
                cfg.setSharedListenPort(false);
            }
            if (wantBedrock) {
                cfg.setCrossplayEnabled(true);
            } else {
                cfg.setCrossplayEnabled(crossplayBox.isSelected());
            }
            cfg.setAllowLocalhost(allowLocalBox.isSelected());
            if (allowLocalBox.isSelected()) {
                cfg.setBindHost("0.0.0.0");
            }
            cfg.setBackwardsCompatible(backwardsBox.isSelected());
            cfg.setOnlineMode(onlineModeBox.isSelected());
            cfg.setResourcePackEnabled(packsBox.isSelected());
            cfg.save();
            com.yapcore.web.DashboardLinkSnapshot.setAllowBedrockPlayers(
                    server.getRootDir(), cfg.getLinkEmbedHome(), wantBedrock);
            if (!fleetScoped) {
                server.getEngine().setMaxPlayers(cfg.getMaxPlayers());
            }
            String synced = fleetScoped
                    ? saveFleetInstanceProps(ramMax, ramMin)
                    : syncPrimaryFleetProps(cfg);
            onSaved.accept(null);
            var ep = new com.yapcore.network.publicity.PublicEndpoint(cfg);
            String join = ep.crossplayJoinAddress();
            String beNote = wantBedrock
                    ? "\nBedrock: " + ep.bedrockJoinAddress() + " (UDP via YaP Link — Start Link if stopped)"
                    : "\nBedrock players: off";
            JOptionPane.showMessageDialog(root,
                    "Settings saved.\nJava: " + (wantJava ? "on" : "off")
                            + " · Bedrock: " + (wantBedrock ? "on" : "off")
                            + "\nPlayers join (Java): " + join
                            + beNote
                            + (synced.isEmpty() ? "" : "\n" + synced)
                            + "\nRestart Link after Bedrock on/off so the UDP edge reloads.",
                    "Saved", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(root, e.getMessage(), "Save Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Persist display name + Folia props for the selected fleet instance only. */
    private String saveFleetInstanceProps(int ramMax, int ramMin) {
        try {
            java.util.HashMap<String, String> body = new java.util.HashMap<>();
            String display = nameField.getText().trim();
            if (display.isEmpty()) {
                display = fleetInstanceId;
            }
            body.put("displayName", display);
            body.put("motd", motdField.getText() == null ? "" : motdField.getText());
            body.put("max-players", Integer.toString((Integer) maxPlayersSpinner.getValue()));
            body.put("view-distance", Integer.toString((Integer) viewDistanceSpinner.getValue()));
            body.put("ramMb", Integer.toString(ramMax));
            body.put("ramMinMb", Integer.toString(ramMin));
            server.fleet().writeInstanceSettings(fleetInstanceId, body);
            return "Saved display name / MOTD / limits → " + fleetInstanceId;
        } catch (Exception e) {
            return "Fleet instance save failed: " + e.getMessage();
        }
    }

    /** Legacy: chassis Settings with no instance selected still mirrors onto primary. */
    private String syncPrimaryFleetProps(ServerConfig cfg) {
        if (!cfg.isFleetEnabled()) {
            return "";
        }
        try {
            String id = server.fleet().store().primaryId();
            if (id == null || id.isBlank()) {
                return "";
            }
            java.util.HashMap<String, String> body = new java.util.HashMap<>();
            body.put("motd", cfg.getMotd() == null ? "" : cfg.getMotd());
            body.put("max-players", Integer.toString(cfg.getMaxPlayers()));
            body.put("view-distance", Integer.toString(cfg.getViewDistance()));
            server.fleet().writeInstanceSettings(id, body);
            return "Also wrote MOTD / max-players / view-distance → fleet instance " + id;
        } catch (Exception e) {
            return "Fleet instance sync skipped: " + e.getMessage();
        }
    }

    private static int asPositiveInt(Object raw) {
        if (raw instanceof Number n) {
            return n.intValue();
        }
        if (raw == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static Integer parsePositive(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            int v = Integer.parseInt(raw.trim());
            return v > 0 ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void styleCheck(JCheckBox box) {
        box.setOpaque(false);
        box.setForeground(GuiTheme.TEXT);
    }

    private static JPanel labeled(String label, java.awt.Component field) {
        JPanel col = new JPanel(new BorderLayout(0, 4));
        col.setOpaque(false);
        JLabel l = new JLabel(label);
        l.setForeground(GuiTheme.MUTED);
        col.add(l, BorderLayout.NORTH);
        col.add(field, BorderLayout.CENTER);
        return col;
    }
}
