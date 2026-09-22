package com.yapcore.gui.panels;

import com.yapcore.config.ServerConfig;
import com.yapcore.fleet.link.BackendHealthBridge;
import com.yapcore.fleet.model.BackendHealth;
import com.yapcore.gui.theme.GuiTheme;
import com.yapcore.server.LinkProcessManager;
import com.yapcore.server.YaPcoreServer;
import com.yapcore.web.DashboardLinkSnapshot;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * YaP Link proxy tab — start/stop separate JVM + dedicated console (Velocity-class).
 */
public final class LinkPanel {

    private final YaPcoreServer server;
    private final LinkProcessManager linkProcess;
    private final JPanel root = new JPanel(new BorderLayout(8, 8));
    private final JLabel stateLabel = new JLabel("Stopped");
    private final JLabel bindLabel = new JLabel("—");
    private final JLabel backendsLabel = new JLabel("—");
    private final JLabel suiteLabel = new JLabel("—");
    private final JLabel embedHint = new JLabel(" ");
    private final DefaultTableModel healthModel = new DefaultTableModel(
            new Object[]{"Name", "Up", "Online", "Max", "Latency", "Source"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable healthTable = new JTable(healthModel);
    private final JTextArea console = new JTextArea(12, 40);
    private final JTextField commandInput = new JTextField();
    private final JButton startBtn = new JButton("Start Link");
    private final JButton stopBtn = new JButton("Stop Link");
    private final Consumer<String> logListener;
    private Timer refreshTimer;

    public LinkPanel(YaPcoreServer server) {
        this.server = server;
        this.linkProcess = server.getLinkProcess();
        root.setOpaque(false);
        root.setBorder(new EmptyBorder(2, 2, 2, 2));
        root.add(buildTop(), BorderLayout.NORTH);
        JPanel center = new JPanel(new BorderLayout(6, 6));
        center.setOpaque(false);
        JScrollPane healthScroll = new JScrollPane(healthTable);
        healthScroll.setPreferredSize(new Dimension(100, 120));
        center.add(healthScroll, BorderLayout.NORTH);
        center.add(buildConsolePanel(), BorderLayout.CENTER);
        root.add(center, BorderLayout.CENTER);

        logListener = text -> SwingUtilities.invokeLater(() -> {
            console.append(text);
            console.setCaretPosition(console.getDocument().getLength());
        });
        linkProcess.addLogListener(logListener);

        GuiTheme.stylePrimary(startBtn);
        GuiTheme.styleDanger(stopBtn);
        startBtn.addActionListener(e -> startLink());
        stopBtn.addActionListener(e -> stopLink());
        commandInput.addActionListener(e -> submitCommand());

        refreshTimer = new Timer(1000, e -> refreshStatus());
        refreshTimer.start();
        refreshStatus();
    }

    public JPanel component() {
        return root;
    }

    public void refreshNow() {
        refreshStatus();
    }

    public void shutdown() {
        if (refreshTimer != null) {
            refreshTimer.stop();
        }
        linkProcess.removeLogListener(logListener);
        if (linkProcess.isRunning()) {
            linkProcess.stop();
        }
    }

    private JPanel buildTop() {
        JPanel panel = GuiTheme.card();
        panel.setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(4, 4, 4, 4);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;

        panel.add(GuiTheme.sectionTitle("YaP Link (network proxy)"), c);
        c.gridy++;
        embedHint.setForeground(GuiTheme.MUTED);
        embedHint.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 11));
        panel.add(embedHint, c);
        c.gridy++;
        panel.add(kv("State", stateLabel), c);
        c.gridy++;
        panel.add(kv("Bind", bindLabel), c);
        c.gridy++;
        panel.add(kv("Backends", backendsLabel), c);
        c.gridy++;
        panel.add(kv("Plugin suite", suiteLabel), c);

        c.gridy++;
        JPanel actions = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
        actions.setOpaque(false);
        actions.add(startBtn);
        actions.add(stopBtn);
        JButton setupOn = new JButton("Forwarding ON");
        setupOn.addActionListener(e -> setBackendForwarding(true));
        actions.add(setupOn);
        JButton setupOff = new JButton("Forwarding OFF");
        setupOff.addActionListener(e -> setBackendForwarding(false));
        actions.add(setupOff);
        JButton configure = new JButton("Configure…");
        configure.addActionListener(e -> openSettings());
        actions.add(configure);
        panel.add(actions, c);

        c.gridy++;
        panel.add(GuiTheme.tip(
                "Runs <code>yap-link.jar</code> as its own process — like Velocity. "
                        + "Use <b>Forwarding ON/OFF</b> to toggle <code>velocity-enabled</code> "
                        + "(same as <code>setup-velocity-forwarding.sh --enable|--disable</code>). "
                        + "Restart Folia after changing."), c);
        return panel;
    }

    private JPanel buildConsolePanel() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setOpaque(false);
        panel.add(GuiTheme.sectionTitle("Link console"), BorderLayout.NORTH);
        console.setEditable(false);
        console.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        console.setBackground(new java.awt.Color(0x0D, 0x11, 0x17));
        console.setForeground(GuiTheme.TEXT);
        console.setLineWrap(true);
        console.setWrapStyleWord(true);
        JScrollPane scroll = new JScrollPane(console);
        scroll.setPreferredSize(new Dimension(100, 220));
        panel.add(scroll, BorderLayout.CENTER);

        JPanel inputRow = new JPanel(new BorderLayout(8, 0));
        inputRow.setOpaque(false);
        commandInput.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        commandInput.setToolTipText("Link commands: help, reload, list, servers, say …, stop");
        JButton send = new JButton("Send");
        GuiTheme.stylePrimary(send);
        send.addActionListener(e -> submitCommand());
        inputRow.add(commandInput, BorderLayout.CENTER);
        inputRow.add(send, BorderLayout.EAST);
        panel.add(inputRow, BorderLayout.SOUTH);
        return panel;
    }

    private void refreshStatus() {
        ServerConfig cfg = server.getConfig();
        boolean embed = cfg.isLinkEmbed();
        boolean running = embed ? false : linkProcess.isRunning();
        if (embed) {
            stateLabel.setText("Embedded (link-embed=true)");
            stateLabel.setForeground(GuiTheme.ACCENT);
            embedHint.setText("Link started in-process at JVM boot — disable link-embed for GUI process control.");
            startBtn.setEnabled(false);
            stopBtn.setEnabled(false);
        } else {
            embedHint.setText(" ");
            stateLabel.setText(running ? "Running" : "Stopped");
            stateLabel.setForeground(running ? GuiTheme.ACCENT : GuiTheme.MUTED);
            startBtn.setEnabled(!running);
            stopBtn.setEnabled(running);
        }

        Path root = server.getRootDir();
        Map<String, Object> snap = DashboardLinkSnapshot.snapshot(
                root, cfg.getLinkEmbedHome(), embed, cfg.isVelocityEnabled());
        bindLabel.setText(String.valueOf(snap.getOrDefault("bind", "—")));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> servers = (List<Map<String, Object>>) snap.get("servers");
        if (servers == null || servers.isEmpty()) {
            backendsLabel.setText("—");
        } else {
            StringBuilder sb = new StringBuilder();
            for (Map<String, Object> s : servers) {
                if (!sb.isEmpty()) {
                    sb.append(", ");
                }
                sb.append(s.get("name")).append('=').append(s.get("address"));
            }
            Object tryList = snap.get("tryServers");
            if (tryList != null) {
                sb.append(" try=").append(tryList);
            }
            backendsLabel.setText(sb.toString());
        }
        suiteLabel.setText(Boolean.TRUE.equals(snap.get("suiteComplete")) ? "Complete" : "Incomplete");

        healthModel.setRowCount(0);
        boolean runningForProbe = embed || linkProcess.isRunning();
        for (BackendHealth h : BackendHealthBridge.collect(root, cfg.getLinkEmbedHome(), runningForProbe)) {
            healthModel.addRow(new Object[]{
                    h.name(),
                    h.up() ? "yes" : "no",
                    h.online(),
                    h.max(),
                    h.latencyMs() < 0 ? "—" : h.latencyMs() + " ms",
                    h.source()
            });
        }
    }

    private void startLink() {
        startBtn.setEnabled(false);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                linkProcess.start();
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                } catch (Exception e) {
                    Throwable c = e;
                    while (c.getCause() != null && c.getCause() != c) {
                        c = c.getCause();
                    }
                    String msg = c.getMessage();
                    if (msg == null || msg.isBlank()) {
                        msg = c.getClass().getSimpleName();
                    }
                    JOptionPane.showMessageDialog(root, msg, "Start Link", JOptionPane.ERROR_MESSAGE);
                    console.append("[Link] Start failed: " + msg + "\n");
                }
                refreshStatus();
            }
        }.execute();
    }

    private void stopLink() {
        stopBtn.setEnabled(false);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                linkProcess.stop();
                return null;
            }

            @Override
            protected void done() {
                refreshStatus();
            }
        }.execute();
    }

    private void submitCommand() {
        String text = commandInput.getText();
        if (text == null || text.isBlank()) {
            return;
        }
        commandInput.setText("");
        String response = linkProcess.dispatchCommand(text);
        if (response != null && !response.isBlank()) {
            console.append(response);
            if (!response.endsWith("\n")) {
                console.append("\n");
            }
        }
    }

    private void openSettings() {
        java.awt.Frame frame = (java.awt.Frame) SwingUtilities.getWindowAncestor(root);
        LinkSettingsDialog dialog = new LinkSettingsDialog(frame, server);
        dialog.setVisible(true);
        refreshStatus();
    }

    private void setBackendForwarding(boolean enable) {
        String flag = enable ? "--enable" : "--disable";
        int ok = JOptionPane.showConfirmDialog(root,
                "Run setup-velocity-forwarding.sh " + flag + "?\n"
                        + (enable
                        ? "Sets velocity-enabled=true — join via YaP Link :25565."
                        : "Sets velocity-enabled=false — direct chassis :25566."),
                "Backend forwarding", JOptionPane.OK_CANCEL_OPTION);
        if (ok != JOptionPane.OK_OPTION) {
            return;
        }
        Path script = server.getRootDir().resolve("scripts/setup/setup-velocity-forwarding.sh");
        console.append("[Link] Running " + script.getFileName() + " " + flag + "…\n");
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                ProcessBuilder pb = new ProcessBuilder("bash", script.toString(), flag);
                pb.directory(server.getRootDir().toFile());
                pb.redirectErrorStream(true);
                Process p = pb.start();
                String out = new String(p.getInputStream().readAllBytes());
                p.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);
                try {
                    server.getConfig().load();
                } catch (Exception ignored) {
                    // Folia paper-global applies on next start
                }
                return out + "\nexit=" + p.exitValue();
            }

            @Override
            protected void done() {
                try {
                    console.append(get());
                    if (!get().endsWith("\n")) {
                        console.append("\n");
                    }
                } catch (Exception e) {
                    console.append("Failed: " + e.getMessage() + "\n");
                }
                refreshStatus();
            }
        }.execute();
    }

    private static JPanel kv(String key, JLabel value) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        JLabel k = new JLabel(key);
        k.setForeground(GuiTheme.MUTED);
        value.setForeground(GuiTheme.TEXT);
        row.add(k, BorderLayout.WEST);
        row.add(value, BorderLayout.CENTER);
        return row;
    }
}
