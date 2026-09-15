package com.yapcore.gui;

import com.yapcore.client.ClientEdition;
import com.yapcore.console.ConsoleBus;
import com.yapcore.gui.panels.ConnectInfoPanel;
import com.yapcore.gui.panels.FleetPanel;
import com.yapcore.gui.panels.LinkPanel;
import com.yapcore.gui.panels.ModulesPanel;
import com.yapcore.gui.panels.NetworkPanel;
import com.yapcore.gui.panels.NginxPanel;
import com.yapcore.gui.panels.PacksPanel;
import com.yapcore.gui.panels.PluginsPanel;
import com.yapcore.gui.panels.SetupPanel;
import com.yapcore.gui.panels.SettingsPanel;
import com.yapcore.gui.panels.TunePanel;
import com.yapcore.gui.theme.GuiTheme;
import com.yapcore.server.YaPcoreServer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;

/** YaPcore Control — fleet-first shell when fleet-enabled; legacy tabs otherwise. */
public final class ControlPanel extends JFrame {

    private final YaPcoreServer server;
    private final ControlPanelFleetContext fleetCtx;
    private final JTextArea console = new JTextArea();
    private final JTextField commandInput = new JTextField();
    private final JLabel statusLabel = new JLabel("Stopped");
    private final JLabel playersLabel = new JLabel("0 / 0");
    private final JLabel heapLabel = new JLabel("—");
    private final JLabel ticksLabel = new JLabel("0");
    private final JLabel dualStackLabel = new JLabel("—");
    private final JLabel activePackLabel = new JLabel("none");
    private final JLabel javaJoinLabel = new JLabel("—");
    private final JLabel subtitle = new JLabel("Network control plane");
    private final JLabel consoleTitle = new JLabel("Console");
    private final JButton startBtn = new JButton("Start");
    private final JButton stopBtn = new JButton("Stop");
    private final JButton restartBtn = new JButton("Restart");
    private String consoleScopeId;
    private final PluginsPanel pluginsPanel;
    private final ModulesPanel modulesPanel;
    private final PacksPanel packsPanel;
    private final NetworkPanel networkPanel;
    private final SettingsPanel settingsPanel;
    private final TunePanel tunePanel;
    private final NginxPanel nginxPanel;
    private final LinkPanel linkPanel;
    private final FleetPanel fleetPanel;
    private final SetupPanel setupPanel;
    private final ConnectInfoPanel connectPanel;
    private ControlFleetShell fleetShell;
    private final JSplitPane split;
    private final Consumer<String> consoleListener;
    private Timer statsTimer;

    public ControlPanel(YaPcoreServer server) {
        super("YaPcore Control — Fleet");
        this.server = server;
        GuiTheme.install();
        this.fleetCtx = new ControlPanelFleetContext(server);
        this.pluginsPanel = new PluginsPanel(server);
        this.modulesPanel = new ModulesPanel(server);
        this.packsPanel = new PacksPanel(server);
        this.networkPanel = new NetworkPanel(server);
        this.settingsPanel = new SettingsPanel(server);
        this.tunePanel = new TunePanel(server);
        this.nginxPanel = new NginxPanel(server);
        this.linkPanel = new LinkPanel(server);
        this.fleetPanel = new FleetPanel(server);
        this.setupPanel = new SetupPanel(server);
        this.connectPanel = new ConnectInfoPanel(server);
        this.networkPanel.setOnSaved(v -> SwingUtilities.invokeLater(this::refreshConnectionUi));
        this.settingsPanel.setOnSaved(v -> SwingUtilities.invokeLater(() -> {
            settingsPanel.reloadFromConfig();
            refreshConnectionUi();
        }));
        this.nginxPanel.setOnSaved(v -> SwingUtilities.invokeLater(this::refreshConnectionUi));
        this.fleetCtx.combo().addActionListener(e -> onContextChanged());

        // Fleet GUI often never calls server.start() — still host packs for YaPItems CMD.
        if (fleetCtx.fleetEnabled()) {
            try {
                server.ensurePackHttp();
            } catch (Exception e) {
                // Non-fatal: Start on an instance will retry via fleetCtx.startSelected.
            }
        }
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
        root.add(ControlPanelChrome.buildHeader(
                fleetCtx, subtitle, javaJoinLabel, startBtn, stopBtn, restartBtn,
                () -> TestLab.open(server.getRootDir()),
                connectPanel::openDashboard,
                this::startSelected,
                this::stopSelected,
                this::restartSelected), BorderLayout.NORTH);
        JPanel main = fleetCtx.fleetEnabled() ? buildFleetMain() : buildLegacyMain();
        split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                ControlPanelChrome.buildConsolePanel(consoleTitle, console, commandInput, this::submitCommand),
                main);
        split.setResizeWeight(0.42);
        split.setContinuousLayout(true);
        split.setOneTouchExpandable(true);
        split.setBorder(null);
        split.setDividerSize(8);
        root.add(split, BorderLayout.CENTER);
        setContentPane(root);

        GuiTheme.fitWindow(this, 1440, 900, 1000, 640);
        SwingUtilities.invokeLater(() -> {
            split.setDividerLocation(0.40);
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
        // console title set after header/console chrome exist
        updateConsoleTitle();
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
                fleetPanel.shutdown();
                if (fleetShell != null) {
                    fleetShell.shutdown();
                }
                if (server.isRunning()) {
                    server.stop();
                }
            }
        });
    }

    private JPanel buildFleetMain() {
        fleetShell = new ControlFleetShell(
                server, fleetCtx, fleetPanel, pluginsPanel, connectPanel, linkPanel,
                networkPanel, nginxPanel, settingsPanel, setupPanel, tunePanel, modulesPanel, packsPanel);
        fleetShell.setOnSelectInstance(id -> {
            pluginsPanel.setFleetInstance(id);
            consoleScopeId = id;
            updateConsoleTitle();
            updateButtonState();
        });
        fleetShell.showFleetHome();
        return fleetShell.component();
    }

    private JPanel buildLegacyMain() {
        JTabbedPane sideTabs = new JTabbedPane(JTabbedPane.TOP, JTabbedPane.SCROLL_TAB_LAYOUT);
        sideTabs.addTab("Setup", GuiTheme.verticalScroll(setupPanel.component()));
        sideTabs.addTab("Connect", GuiTheme.verticalScroll(connectPanel.component()));
        sideTabs.addTab("Access", networkPanel.component());
        sideTabs.addTab("nginx", nginxPanel.component());
        sideTabs.addTab("Link", linkPanel.component());
        sideTabs.addTab("Fleet", fleetPanel.component());
        sideTabs.addTab("Settings", settingsPanel.component());
        sideTabs.addTab("Tune", GuiTheme.verticalScroll(tunePanel.component()));
        sideTabs.addTab("Status", GuiTheme.verticalScroll(ControlPanelChrome.buildStatusTab(
                statusLabel, playersLabel, heapLabel, ticksLabel, dualStackLabel, activePackLabel)));
        sideTabs.addTab("Plugins", pluginsPanel.component());
        sideTabs.addTab("Modules", modulesPanel.component());
        sideTabs.addTab("Packs", packsPanel.component());
        JPanel side = new JPanel(new BorderLayout());
        side.setOpaque(false);
        side.add(sideTabs, BorderLayout.CENTER);
        return side;
    }

    private void onContextChanged() {
        if (fleetCtx.isRefreshing()) {
            return;
        }
        updateButtonState();
        String id = fleetCtx.instanceId();
        consoleScopeId = id;
        updateConsoleTitle();
        if (fleetShell == null) {
            pluginsPanel.setFleetInstance(id);
            return;
        }
        if (id != null) {
            fleetShell.showInstance(id);
            pluginsPanel.setFleetInstance(id);
        } else {
            // NETWORK in the combo = fleet home (game servers). YaP Link is opened
            // explicitly via the rail card / Open YaP Link button — not this combo.
            fleetShell.showFleetHome();
            pluginsPanel.setFleetInstance(null);
        }
    }

    private void updateConsoleTitle() {
        if (!fleetCtx.fleetEnabled()) {
            consoleTitle.setText("Console");
            return;
        }
        String id = consoleScopeId != null ? consoleScopeId : fleetCtx.instanceId();
        if (id == null || id.isBlank()) {
            String primary = server.fleet().store().primaryId();
            consoleTitle.setText("Console · Fleet home (commands → "
                    + (primary == null || primary.isBlank() ? "primary" : primary) + ")");
        } else {
            consoleTitle.setText("Console · " + id);
        }
    }

    private void submitCommand() {
        String text = commandInput.getText();
        if (text == null || text.isBlank()) {
            return;
        }
        commandInput.setText("");
        String scope = fleetCtx.instanceId() == null ? "network" : fleetCtx.instanceId();
        ConsoleBus.get().publish("> [" + scope + "] " + text);
        String response = fleetCtx.dispatchCommand(text);
        if (response != null && !response.isBlank()) {
            for (String line : response.split("\n")) {
                ConsoleBus.get().publish(line);
            }
        }
        refreshConnectionUi();
        updateButtonState();
    }

    private void startSelected() {
        startBtn.setEnabled(false);
        runLifecycle(() -> {
            if (fleetCtx.fleetEnabled() && fleetCtx.instanceId() != null) {
                fleetCtx.startSelected();
            } else if (fleetCtx.fleetEnabled()) {
                if (!server.isRunning()) {
                    server.start();
                } else {
                    fleetCtx.startSelected();
                }
            } else {
                server.start();
            }
        });
    }

    private void stopSelected() {
        stopBtn.setEnabled(false);
        runLifecycle(() -> {
            if (fleetCtx.fleetEnabled() && fleetCtx.instanceId() != null) {
                fleetCtx.stopSelected();
            } else if (fleetCtx.fleetEnabled()) {
                server.fleet().stopAllLocal();
            } else {
                server.stop();
            }
        });
    }

    private void restartSelected() {
        restartBtn.setEnabled(false);
        runLifecycle(() -> {
            if (fleetCtx.fleetEnabled() && fleetCtx.instanceId() == null) {
                String primary = server.fleet().store().primaryId();
                if (primary == null || primary.isBlank()) {
                    primary = "lobby";
                }
                server.fleet().restartInstance(primary);
            } else {
                fleetCtx.restartSelected();
            }
        });
    }

    private void runLifecycle(LifecycleOp op) {
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                op.run();
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(ControlPanel.this,
                            e.getMessage(), "Fleet", JOptionPane.ERROR_MESSAGE);
                }
                refreshConnectionUi();
                updateButtonState();
                fleetPanel.refreshNow();
            }
        }.execute();
    }

    private void refreshConnectionUi() {
        fleetCtx.refresh();
        connectPanel.refresh();
        networkPanel.refresh();
        subtitle.setText(fleetCtx.fleetEnabled()
                ? "Fleet — left rail picks the server · Plugins opens first"
                : "Control · Connect · Access · Settings");
        javaJoinLabel.setText("<html>" + ControlPanelChrome.escapeHtml(fleetCtx.headerSummary()) + "</html>");
        updateConsoleTitle();
        updateButtonState();
    }

    private void refreshStats() {
        boolean on = server.isRunning()
                || (fleetCtx.fleetEnabled() && server.fleet().isPrimaryRunning());
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
        if (fleetCtx.fleetEnabled() && (System.currentTimeMillis() / 500) % 4 == 0) {
            fleetCtx.refresh();
            javaJoinLabel.setText("<html>" + ControlPanelChrome.escapeHtml(fleetCtx.headerSummary()) + "</html>");
        }
    }

    private void updateButtonState() {
        startBtn.setText(fleetCtx.startButtonLabel());
        stopBtn.setText(fleetCtx.stopButtonLabel());
        boolean fleet = fleetCtx.fleetEnabled();
        restartBtn.setText(fleet && fleetCtx.instanceId() == null ? "Restart primary" : "Restart");
        restartBtn.setEnabled(fleet || server.isRunning());
        startBtn.setEnabled(true);
        stopBtn.setEnabled(true);
    }

    @FunctionalInterface
    private interface LifecycleOp {
        void run() throws Exception;
    }
}
