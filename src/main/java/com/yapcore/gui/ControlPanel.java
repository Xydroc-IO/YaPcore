package com.yapcore.gui;

import com.yapcore.client.ClientEdition;
import com.yapcore.console.ConsoleBus;
import com.yapcore.gui.panels.LinkPanel;
import com.yapcore.gui.panels.ConnectInfoPanel;
import com.yapcore.gui.panels.NetworkPanel;
import com.yapcore.gui.panels.ModulesPanel;
import com.yapcore.gui.panels.NginxPanel;
import com.yapcore.gui.panels.PacksPanel;
import com.yapcore.gui.panels.PluginsPanel;
import com.yapcore.gui.panels.TunePanel;
import com.yapcore.gui.panels.SettingsPanel;
import com.yapcore.gui.theme.GuiTheme;
import com.yapcore.server.YaPcoreServer;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.function.Consumer;

/** YaPcore control window — Connect, Access, Settings, console. */
public final class ControlPanel extends JFrame {

    private final YaPcoreServer server;
    private final JTextArea console = new JTextArea();
    private final JTextField commandInput = new JTextField();
    private final JLabel statusLabel = new JLabel("Stopped");
    private final JLabel playersLabel = new JLabel("0 / 0");
    private final JLabel heapLabel = new JLabel("—");
    private final JLabel ticksLabel = new JLabel("0");
    private final JLabel dualStackLabel = new JLabel("—");
    private final JLabel activePackLabel = new JLabel("none");
    private final JLabel javaJoinLabel = new JLabel("—");
    private final JButton startBtn = new JButton("Start Server");
    private final JButton stopBtn = new JButton("Stop Server");
    private final PluginsPanel pluginsPanel;
    private final ModulesPanel modulesPanel;
    private final PacksPanel packsPanel;
    private final NetworkPanel networkPanel;
    private final SettingsPanel settingsPanel;
    private final TunePanel tunePanel;
    private final NginxPanel nginxPanel;
    private final LinkPanel linkPanel;
    private final ConnectInfoPanel connectPanel;
    private final JTabbedPane sideTabs = new JTabbedPane(JTabbedPane.TOP, JTabbedPane.SCROLL_TAB_LAYOUT);
    private final JSplitPane split;
    private final Consumer<String> consoleListener;
    private Timer statsTimer;

    public ControlPanel(YaPcoreServer server) {
        super("YaPcore Control");
        this.server = server;
        GuiTheme.install();
        this.pluginsPanel = new PluginsPanel(server);
        this.modulesPanel = new ModulesPanel(server);
        this.packsPanel = new PacksPanel(server);
        this.networkPanel = new NetworkPanel(server);
        this.settingsPanel = new SettingsPanel(server);
        this.tunePanel = new TunePanel(server);
        this.nginxPanel = new NginxPanel(server);
        this.linkPanel = new LinkPanel(server);
        this.connectPanel = new ConnectInfoPanel(server);
        this.networkPanel.setOnSaved(v -> SwingUtilities.invokeLater(this::refreshConnectionUi));
        this.settingsPanel.setOnSaved(v -> SwingUtilities.invokeLater(() -> {
            settingsPanel.reloadFromConfig();
            refreshConnectionUi();
        }));
        this.nginxPanel.setOnSaved(v -> SwingUtilities.invokeLater(this::refreshConnectionUi));

        try {
            java.nio.file.Path icon = server.getRootDir().resolve("branding/yapcore-icon.png");
            if (java.nio.file.Files.isRegularFile(icon)) {
                setIconImage(javax.imageio.ImageIO.read(icon.toFile()));
            }
        } catch (Exception ignored) {
        }

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        getContentPane().setBackground(GuiTheme.BG);

        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(new EmptyBorder(12, 12, 12, 12));
        root.setBackground(GuiTheme.BG);
        root.add(buildHeader(), BorderLayout.NORTH);
        split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildConsolePanel(), buildSidePanel());
        split.setResizeWeight(0.55);
        split.setContinuousLayout(true);
        split.setOneTouchExpandable(true);
        split.setBorder(null);
        split.setDividerSize(8);
        root.add(split, BorderLayout.CENTER);
        setContentPane(root);

        // Fit usable screen — no pack() fight with preferred sizes
        GuiTheme.fitWindow(this, 1280, 820, 920, 600);
        SwingUtilities.invokeLater(() -> {
            split.setDividerLocation(0.58);
            revalidate();
        });

        consoleListener = line -> SwingUtilities.invokeLater(() -> {
            console.append(line);
            console.setCaretPosition(console.getDocument().getLength());
        });
        ConsoleBus.get().addListener(consoleListener);
        console.setText(ConsoleBus.get().getRecentText());
        server.getPluginManager().addListener(list -> SwingUtilities.invokeLater(pluginsPanel::refresh));
        server.getResourcePacks().addListener(list -> SwingUtilities.invokeLater(packsPanel::refresh));
        commandInput.addActionListener(e -> submitCommand());
        updateButtonState();
        refreshConnectionUi();
        sideTabs.setSelectedIndex(0);
        statsTimer = new Timer(500, e -> refreshStats());
        statsTimer.start();
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                if (statsTimer != null) {
                    statsTimer.stop();
                }
                ConsoleBus.get().removeListener(consoleListener);
                linkPanel.shutdown();
                if (server.isRunning()) {
                    server.stop();
                }
            }
        });
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                // Keep side pane usable when the window shrinks
                int w = getWidth();
                if (w > 0 && split.getDividerLocation() > w - 280) {
                    split.setDividerLocation(Math.max(360, w - 360));
                }
            }
        });
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setOpaque(false);
        JLabel brand = new JLabel("YaPcore");
        brand.setFont(new Font("Segoe UI", Font.BOLD, 24));
        brand.setForeground(GuiTheme.ACCENT);
        JLabel subtitle = new JLabel("Control · Connect · Access · Settings");
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        subtitle.setForeground(GuiTheme.MUTED);
        JPanel titles = new JPanel(new GridBagLayout());
        titles.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.anchor = GridBagConstraints.WEST;
        titles.add(brand, c);
        c.gridy = 1;
        titles.add(subtitle, c);
        c.gridy = 2;
        c.insets = new Insets(4, 0, 0, 0);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        javaJoinLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        javaJoinLabel.setForeground(GuiTheme.TEXT);
        titles.add(javaJoinLabel, c);
        header.add(titles, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        JButton testLabBtn = new JButton("Test Lab");
        GuiTheme.stylePrimary(testLabBtn);
        testLabBtn.addActionListener(e -> TestLab.open(server.getRootDir()));
        JButton dashboardBtn = new JButton("Web Dashboard");
        GuiTheme.stylePrimary(dashboardBtn);
        dashboardBtn.addActionListener(e -> connectPanel.openDashboard());
        GuiTheme.stylePrimary(startBtn);
        GuiTheme.styleDanger(stopBtn);
        startBtn.addActionListener(e -> startServer());
        stopBtn.addActionListener(e -> stopServer());
        actions.add(testLabBtn);
        actions.add(dashboardBtn);
        actions.add(startBtn);
        actions.add(stopBtn);
        header.add(actions, BorderLayout.EAST);
        return header;
    }

    private JPanel buildConsolePanel() {
        JPanel panel = GuiTheme.card();
        panel.setLayout(new BorderLayout(8, 8));
        panel.setMinimumSize(new Dimension(360, 240));
        panel.add(GuiTheme.sectionTitle("Live Console"), BorderLayout.NORTH);
        console.setEditable(false);
        console.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        console.setBackground(new Color(0x0D, 0x11, 0x17));
        console.setForeground(GuiTheme.TEXT);
        console.setCaretColor(GuiTheme.ACCENT);
        console.setLineWrap(true);
        console.setWrapStyleWord(true);
        panel.add(new JScrollPane(console), BorderLayout.CENTER);
        JPanel inputRow = new JPanel(new BorderLayout(8, 0));
        inputRow.setOpaque(false);
        commandInput.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        JButton send = new JButton("Send");
        GuiTheme.stylePrimary(send);
        send.addActionListener(e -> submitCommand());
        inputRow.add(commandInput, BorderLayout.CENTER);
        inputRow.add(send, BorderLayout.EAST);
        panel.add(inputRow, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildSidePanel() {
        JPanel side = new JPanel(new BorderLayout());
        side.setOpaque(false);
        side.setMinimumSize(new Dimension(320, 240));
        side.setPreferredSize(new Dimension(420, 600));
        sideTabs.addTab("Connect", GuiTheme.verticalScroll(connectPanel.component()));
        sideTabs.addTab("Access", networkPanel.component());
        sideTabs.addTab("nginx", nginxPanel.component());
        sideTabs.addTab("Link", linkPanel.component());
        sideTabs.addTab("Settings", settingsPanel.component());
        sideTabs.addTab("Tune", GuiTheme.verticalScroll(tunePanel.component()));
        sideTabs.addTab("Status", GuiTheme.verticalScroll(buildStatusTab()));
        sideTabs.addTab("Plugins", pluginsPanel.component());
        sideTabs.addTab("Modules", modulesPanel.component());
        sideTabs.addTab("Packs", packsPanel.component());
        side.add(sideTabs, BorderLayout.CENTER);
        return side;
    }

    private JPanel buildStatusTab() {
        JPanel runtime = GuiTheme.card();
        runtime.setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(6, 4, 6, 4);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        runtime.add(GuiTheme.sectionTitle("Runtime"), c);
        c.gridy++;
        runtime.add(kv("State", statusLabel), c);
        c.gridy++;
        runtime.add(kv("Players", playersLabel), c);
        c.gridy++;
        runtime.add(kv("Heap", heapLabel), c);
        c.gridy++;
        runtime.add(kv("Ticks", ticksLabel), c);
        c.gridy++;
        runtime.add(kv("Clients", dualStackLabel), c);
        c.gridy++;
        runtime.add(kv("Active packs", activePackLabel), c);
        c.gridy++;
        c.weighty = 1;
        runtime.add(new JLabel(), c);
        return runtime;
    }

    private void submitCommand() {
        String text = commandInput.getText();
        if (text == null || text.isBlank()) {
            return;
        }
        commandInput.setText("");
        ConsoleBus.get().publish("> " + text);
        String response = server.executeCommand(text);
        if (response != null && !response.isBlank()) {
            for (String line : response.split("\n")) {
                ConsoleBus.get().publish(line);
            }
        }
        refreshConnectionUi();
        updateButtonState();
    }

    private void startServer() {
        startBtn.setEnabled(false);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                server.start();
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(ControlPanel.this,
                            "Failed to start: " + e.getMessage(), "Start Error", JOptionPane.ERROR_MESSAGE);
                }
                refreshConnectionUi();
                updateButtonState();
            }
        }.execute();
    }

    private void stopServer() {
        stopBtn.setEnabled(false);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                server.stop();
                return null;
            }

            @Override
            protected void done() {
                refreshConnectionUi();
                updateButtonState();
            }
        }.execute();
    }

    private void refreshConnectionUi() {
        connectPanel.refresh();
        networkPanel.refresh();
        int port = server.getConfig().getPort();
        String pub = server.publicEndpoint().crossplayJoinAddress();
        javaJoinLabel.setText("<html>This PC: <b>127.0.0.1:" + port
                + "</b> &nbsp;·&nbsp; Public: <b>" + escapeHtml(pub) + "</b></html>");
    }

    private static String escapeHtml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void refreshStats() {
        boolean on = server.isRunning();
        statusLabel.setText(on ? "Running" : "Stopped");
        statusLabel.setForeground(on ? GuiTheme.ACCENT : new Color(0xE3, 0x6B, 0x6B));
        playersLabel.setText(server.getOnlinePlayers() + " / " + server.getMaxPlayers());
        ticksLabel.setText(Long.toString(server.getEngine().gameCore().getTickCounter()));
        Runtime rt = Runtime.getRuntime();
        long used = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long max = rt.maxMemory() / (1024 * 1024);
        heapLabel.setText(used + " / " + max + " MB");
        dualStackLabel.setText("J " + server.getGateway().getClients().countEdition(ClientEdition.JAVA)
                + " / B " + server.getGateway().getClients().countEdition(ClientEdition.BEDROCK));
        var actives = server.getResourcePacks().getActivePacks();
        activePackLabel.setText(actives.isEmpty() ? "none"
                : actives.stream().map(p -> p.getFileName()).reduce((a, b) -> a + ", " + b).orElse("none"));
        updateButtonState();
    }

    private void updateButtonState() {
        boolean on = server.isRunning();
        startBtn.setEnabled(!on);
        stopBtn.setEnabled(on);
    }

    private static JPanel kv(String key, JLabel value) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        JLabel k = new JLabel(key);
        k.setForeground(GuiTheme.MUTED);
        value.setForeground(GuiTheme.TEXT);
        value.setFont(new Font("Segoe UI", Font.BOLD, 14));
        row.add(k, BorderLayout.WEST);
        row.add(value, BorderLayout.EAST);
        return row;
    }
}
