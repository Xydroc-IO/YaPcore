package com.yapcore.gui;

import com.yapcore.gui.panels.ConnectInfoPanel;
import com.yapcore.gui.panels.FleetPanel;
import com.yapcore.gui.panels.LinkPanel;
import com.yapcore.gui.panels.ModulesPanel;
import com.yapcore.gui.panels.NetworkPanel;
import com.yapcore.gui.panels.NginxPanel;
import com.yapcore.gui.panels.PacksPanel;
import com.yapcore.gui.panels.PluginsPanel;
import com.yapcore.gui.panels.SettingsPanel;
import com.yapcore.gui.panels.TunePanel;
import com.yapcore.gui.theme.GuiTheme;
import com.yapcore.server.YaPcoreServer;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;

/**
 * Fleet-first main workspace: server rail + Fleet home + scoped tools.
 * Replaces the old “Connect-first tabs with Fleet buried” shell.
 */
public final class ControlFleetShell {

    private final YaPcoreServer server;
    private final ControlPanelFleetContext fleetCtx;
    private final FleetPanel fleetPanel;
    private final PluginsPanel pluginsPanel;
    private final ConnectInfoPanel connectPanel;
    private final LinkPanel linkPanel;
    private final NetworkPanel networkPanel;
    private final NginxPanel nginxPanel;
    private final SettingsPanel settingsPanel;
    private final TunePanel tunePanel;
    private final ModulesPanel modulesPanel;
    private final PacksPanel packsPanel;
    private final JPanel root = new JPanel(new BorderLayout(8, 8));
    private final JPanel railList = new JPanel();
    private final CardLayout cards = new CardLayout();
    private final JPanel cardHost = new JPanel(cards);
    private final JLabel workspaceTitle = new JLabel("Fleet home");
    private JTabbedPane instanceTabs;
    private JTabbedPane networkTabs;
    private Timer railTimer;
    private Consumer<String> onSelectInstance = id -> {
    };

    public ControlFleetShell(
            YaPcoreServer server,
            ControlPanelFleetContext fleetCtx,
            FleetPanel fleetPanel,
            PluginsPanel pluginsPanel,
            ConnectInfoPanel connectPanel,
            LinkPanel linkPanel,
            NetworkPanel networkPanel,
            NginxPanel nginxPanel,
            SettingsPanel settingsPanel,
            TunePanel tunePanel,
            ModulesPanel modulesPanel,
            PacksPanel packsPanel) {
        this.server = server;
        this.fleetCtx = fleetCtx;
        this.fleetPanel = fleetPanel;
        this.pluginsPanel = pluginsPanel;
        this.connectPanel = connectPanel;
        this.linkPanel = linkPanel;
        this.networkPanel = networkPanel;
        this.nginxPanel = nginxPanel;
        this.settingsPanel = settingsPanel;
        this.tunePanel = tunePanel;
        this.modulesPanel = modulesPanel;
        this.packsPanel = packsPanel;
        root.setOpaque(false);
        root.add(buildRail(), BorderLayout.WEST);
        root.add(buildWorkspace(), BorderLayout.CENTER);
        fleetPanel.setOpenLinkAction(this::showLink);
        refreshRail();
        showCard("fleet");
        railTimer = new Timer(2000, e -> refreshRail());
        railTimer.start();
    }

    public JPanel component() {
        return root;
    }

    public void setOnSelectInstance(Consumer<String> listener) {
        this.onSelectInstance = listener == null ? id -> {
        } : listener;
    }

    public void shutdown() {
        if (railTimer != null) {
            railTimer.stop();
        }
    }

    public void showInstance(String id) {
        pluginsPanel.setFleetInstance(id);
        settingsPanel.setFleetInstance(id);
        showCard("instance");
        workspaceTitle.setText("Server · " + id + " — plugins first, then setup");
        if (instanceTabs != null) {
            instanceTabs.setSelectedIndex(0);
        }
    }

    public void showFleetHome() {
        settingsPanel.setFleetInstance(null);
        showCard("fleet");
        workspaceTitle.setText("Fleet home — every game server");
    }

    public void showLink() {
        showCard("network");
        workspaceTitle.setText("YaP Link — network edge for all game servers");
        if (networkTabs != null) {
            networkTabs.setSelectedIndex(0);
        }
        // Do not set combo → NETWORK here: ControlPanel.onContextChanged maps NETWORK
        // to showFleetHome() and would undo this navigation.
        connectPanel.refresh();
        linkPanel.refreshNow();
    }

    public void showConnect() {
        showCard("network");
        workspaceTitle.setText("Connect — join addresses (Link edge + backends)");
        if (networkTabs != null && networkTabs.getTabCount() > 1) {
            networkTabs.setSelectedIndex(1);
        }
        connectPanel.refresh();
        // Keep current combo selection; avoid bouncing to fleet home.
    }

    public void showNetwork() {
        showCard("network");
        workspaceTitle.setText("Access & nginx — network edge helpers");
        if (networkTabs != null && networkTabs.getTabCount() > 2) {
            networkTabs.setSelectedIndex(2);
        }
    }

    private void showCard(String name) {
        cards.show(cardHost, name);
    }

    private JPanel buildRail() {
        JPanel rail = GuiTheme.card();
        rail.setLayout(new BorderLayout(6, 6));
        rail.setPreferredSize(new Dimension(220, 400));
        rail.setMinimumSize(new Dimension(180, 200));
        JLabel title = GuiTheme.sectionTitle("Network");
        rail.add(title, BorderLayout.NORTH);
        railList.setLayout(new BoxLayout(railList, BoxLayout.Y_AXIS));
        railList.setOpaque(false);
        JScrollPane scroll = new JScrollPane(railList);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setOpaque(false);
        rail.add(scroll, BorderLayout.CENTER);
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        bottom.setOpaque(false);
        JButton home = new JButton("Fleet home");
        GuiTheme.stylePrimary(home);
        home.addActionListener(e -> {
            fleetCtx.combo().setSelectedItem(ControlPanelFleetContext.NETWORK);
            showFleetHome();
        });
        JButton access = new JButton("Access / nginx");
        access.addActionListener(e -> showNetwork());
        bottom.add(home);
        bottom.add(access);
        rail.add(bottom, BorderLayout.SOUTH);
        return rail;
    }

    private JPanel buildWorkspace() {
        JPanel wrap = new JPanel(new BorderLayout(6, 6));
        wrap.setOpaque(false);
        workspaceTitle.setFont(new Font("Segoe UI", Font.BOLD, 16));
        workspaceTitle.setForeground(GuiTheme.TEXT);
        workspaceTitle.setBorder(new EmptyBorder(0, 4, 6, 4));
        wrap.add(workspaceTitle, BorderLayout.NORTH);

        cardHost.setOpaque(false);
        cardHost.add(fleetPanel.component(), "fleet");
        cardHost.add(buildInstanceWorkspace(), "instance");
        cardHost.add(buildNetworkWorkspace(), "network");
        wrap.add(cardHost, BorderLayout.CENTER);
        return wrap;
    }

    private JPanel buildInstanceWorkspace() {
        instanceTabs = new JTabbedPane(JTabbedPane.TOP, JTabbedPane.SCROLL_TAB_LAYOUT);
        instanceTabs.addTab("Plugins", pluginsPanel.component());
        instanceTabs.addTab("Settings", settingsPanel.component());
        instanceTabs.addTab("Tune", GuiTheme.verticalScroll(tunePanel.component()));
        instanceTabs.addTab("Modules", modulesPanel.component());
        instanceTabs.addTab("Packs", packsPanel.component());
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.add(instanceTabs, BorderLayout.CENTER);
        return p;
    }

    private JPanel buildNetworkWorkspace() {
        networkTabs = new JTabbedPane(JTabbedPane.TOP, JTabbedPane.SCROLL_TAB_LAYOUT);
        // Connect lives only here — a JComponent can have one parent; duplicating blanked the tab.
        networkTabs.addTab("Link", linkPanel.component());
        networkTabs.addTab("Connect", GuiTheme.verticalScroll(connectPanel.component()));
        networkTabs.addTab("Access", networkPanel.component());
        networkTabs.addTab("nginx", nginxPanel.component());
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.add(networkTabs, BorderLayout.CENTER);
        return p;
    }

    private void refreshRail() {
        if (!server.getConfig().isFleetEnabled()) {
            return;
        }
        railList.removeAll();
        railList.add(linkRailCard());
        railList.add(Box.createVerticalStrut(8));
        JLabel games = new JLabel("Game servers");
        games.setForeground(GuiTheme.MUTED);
        games.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        games.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        railList.add(games);
        railList.add(Box.createVerticalStrut(4));
        try {
            Map<String, Object> snap = server.fleet().statusSnapshot();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> instances = (List<Map<String, Object>>) snap.get("instances");
            if (instances != null) {
                for (Map<String, Object> i : instances) {
                    railList.add(railButton(i));
                    railList.add(Box.createVerticalStrut(6));
                }
            }
        } catch (Exception e) {
            JLabel err = new JLabel(e.getMessage());
            err.setForeground(GuiTheme.MUTED);
            railList.add(err);
        }
        railList.revalidate();
        railList.repaint();
    }

    private JPanel linkRailCard() {
        boolean running = false;
        try {
            running = server.getLinkProcess() != null && server.getLinkProcess().isRunning();
        } catch (Exception ignored) {
        }
        if (!running) {
            try {
                running = server.getConfig().isLinkEmbed() && server.isRunning();
            } catch (Exception ignored) {
            }
        }
        JPanel card = new JPanel(new BorderLayout(4, 2));
        card.setOpaque(true);
        card.setBackground(new Color(0x12, 0x22, 0x2E));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(GuiTheme.ACCENT),
                new EmptyBorder(8, 10, 8, 10)));
        JLabel title = new JLabel("YaP Link — setup");
        title.setFont(new Font("Segoe UI", Font.BOLD, 13));
        title.setForeground(GuiTheme.TEXT);
        JLabel meta = new JLabel(":" + linkEdgePort()
                + "  click to configure  " + (running ? "● up" : "○ stopped"));
        meta.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        meta.setForeground(running ? GuiTheme.ACCENT : GuiTheme.MUTED);
        card.add(title, BorderLayout.NORTH);
        card.add(meta, BorderLayout.SOUTH);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
        card.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        card.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        card.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                showLink();
            }
        });
        return card;
    }

    private JPanel railButton(Map<String, Object> i) {
        String id = String.valueOf(i.get("id"));
        String name = String.valueOf(i.getOrDefault("displayName", id));
        String state = String.valueOf(i.getOrDefault("state", "?"));
        boolean running = Boolean.TRUE.equals(i.get("running")) || "RUNNING".equalsIgnoreCase(state);
        JPanel card = new JPanel(new BorderLayout(4, 2));
        card.setOpaque(true);
        card.setBackground(new Color(0x15, 0x1C, 0x26));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0x2A, 0x35, 0x40)),
                new EmptyBorder(8, 10, 8, 10)));
        JLabel title = new JLabel(name);
        title.setFont(new Font("Segoe UI", Font.BOLD, 13));
        title.setForeground(GuiTheme.TEXT);
        Object pc = i.get("pluginCount");
        JLabel meta = new JLabel(":" + i.get("port")
                + (pc != null ? " · " + pc + " plugins" : "")
                + "  " + (running ? "● up" : "○ " + state));
        meta.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        meta.setForeground(running ? GuiTheme.ACCENT : GuiTheme.MUTED);
        card.add(title, BorderLayout.NORTH);
        card.add(meta, BorderLayout.SOUTH);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
        card.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        card.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        card.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                selectInstance(id);
            }
        });
        return card;
    }

    private int linkEdgePort() {
        return new com.yapcore.network.publicity.PublicEndpoint(server.getConfig()).advertisedJavaPort();
    }

    private void selectInstance(String id) {
        // sync combo label
        for (int i = 0; i < fleetCtx.combo().getItemCount(); i++) {
            String item = fleetCtx.combo().getItemAt(i);
            if (id.equals(ControlPanelFleetContext.instanceIdFrom(item))) {
                fleetCtx.combo().setSelectedIndex(i);
                break;
            }
        }
        showInstance(id);
        onSelectInstance.accept(id);
        SwingUtilities.invokeLater(() -> fleetPanel.refreshNow());
    }
}
