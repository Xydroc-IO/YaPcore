package com.yapcore.gui;

import com.yapcore.client.ClientEdition;
import com.yapcore.console.ConsoleBus;
import com.yapcore.gui.panels.ConnectInfoPanel;
import com.yapcore.gui.panels.NetworkPanel;
import com.yapcore.gui.panels.ModulesPanel;
import com.yapcore.gui.panels.NginxPanel;
import com.yapcore.gui.panels.PacksPanel;
import com.yapcore.gui.panels.PluginsPanel;
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
    private final NginxPanel nginxPanel;
    private final ConnectInfoPanel connectPanel;
    private final JTabbedPane sideTabs = new JTabbedPane();
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
        this.nginxPanel = new NginxPanel(server);
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
        setMinimumSize(new Dimension(1100, 720));
        setPreferredSize(new Dimension(1240, 800));
        getContentPane().setBackground(GuiTheme.BG);

        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(new EmptyBorder(14, 14, 14, 14));
        root.setBackground(GuiTheme.BG);
        root.add(buildHeader(), BorderLayout.NORTH);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildConsolePanel(), buildSidePanel());
        split.setResizeWeight(0.58);
        split.setBorder(null);
        root.add(split, BorderLayout.CENTER);
        setContentPane(root);
        pack();
        setLocationRelativeTo(null);

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
        // Open on Connect so join addresses are the first thing you see
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
                if (server.isRunning()) {
                    server.stop();
                }
            }
        });
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel brand = new JLabel("YaPcore");
        brand.setFont(new Font("Segoe UI", Font.BOLD, 26));
        brand.setForeground(GuiTheme.ACCENT);
        JLabel subtitle = new JLabel("Control · Connect · Access · nginx · Settings · Tests");
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 13));
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
        c.insets = new Insets(6, 0, 0, 0);
        javaJoinLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 13));
        javaJoinLabel.setForeground(GuiTheme.TEXT);
        titles.add(javaJoinLabel, c);
        header.add(titles, BorderLayout.WEST);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        JButton testLabBtn = new JButton("Test Lab");
        GuiTheme.stylePrimary(testLabBtn);
        testLabBtn.addActionListener(e -> TestLab.open(server.getRootDir()));
        GuiTheme.stylePrimary(startBtn);
        GuiTheme.styleDanger(stopBtn);
        startBtn.addActionListener(e -> startServer());
        stopBtn.addActionListener(e -> stopServer());
        actions.add(testLabBtn);
        actions.add(startBtn);
        actions.add(stopBtn);
        header.add(actions, BorderLayout.EAST);
        return header;
    }

    private JPanel buildConsolePanel() {
        JPanel panel = GuiTheme.card();
        panel.setLayout(new BorderLayout(8, 8));
        panel.add(GuiTheme.sectionTitle("Live Console"), BorderLayout.NORTH);
        console.setEditable(false);
        console.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        console.setBackground(new Color(0x0D, 0x11, 0x17));
        console.setForeground(GuiTheme.TEXT);
        console.setCaretColor(GuiTheme.ACCENT);
        console.setLineWrap(true);
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
        side.setPreferredSize(new Dimension(420, 560));
        sideTabs.addTab("Connect", wrapScroll(connectPanel.component()));
        sideTabs.addTab("Access", networkPanel.component());
        sideTabs.addTab("nginx", nginxPanel.component());
        sideTabs.addTab("Settings", settingsPanel.component());
        sideTabs.addTab("Status", wrapScroll(buildStatusTab()));
        sideTabs.addTab("Plugins", pluginsPanel.component());
        sideTabs.addTab("Modules", modulesPanel.component());
        sideTabs.addTab("Packs", packsPanel.component());
        side.add(sideTabs, BorderLayout.CENTER);
        return side;
    }

    private static JScrollPane wrapScroll(JPanel inner) {
        JScrollPane scroll = new JScrollPane(inner);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        return scroll;
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
        runtime.add(kv("Active pack", activePackLabel), c);
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
        javaJoinLabel.setText("This PC: 127.0.0.1:" + server.getConfig().getPort()
                + "   ·   Public: " + server.publicEndpoint().crossplayJoinAddress());
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
        activePackLabel.setText(server.getResourcePacks().getActivePack()
                .map(p -> p.getFileName()).orElse("none"));
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
