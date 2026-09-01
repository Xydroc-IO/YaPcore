package com.yapcore.tab;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TabConfig {

    private final JavaPlugin plugin;
    private List<String> header = List.of();
    private List<String> footer = List.of();
    private List<String> sidebar = List.of();
    private boolean sidebarEnabled = true;
    private boolean nametagTeams = true;
    private int refreshSeconds = 3;
    private boolean networkSyncEnabled = true;
    private String serverId = "default";
    private int networkSyncHeartbeatSeconds = 30;
    private boolean bossBarEnabled;
    private boolean bossBarWelcomeOnJoin = true;
    private String bossBarTitle = "&6&lWelcome";
    private String bossBarSubtitle = "";
    private BossBar.Color bossBarColor = BossBar.Color.YELLOW;
    private int bossBarDurationSeconds = 8;

    public TabConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();
        header = c.getStringList("header");
        footer = c.getStringList("footer");
        sidebar = c.getStringList("sidebar.lines");
        sidebarEnabled = c.getBoolean("sidebar.enabled", true);
        nametagTeams = c.getBoolean("nametag-teams", true);
        refreshSeconds = Math.max(1, c.getInt("refresh-seconds", 3));
        networkSyncEnabled = c.getBoolean("network-sync.enabled", true);
        serverId = c.getString("network-sync.server-id", "default");
        networkSyncHeartbeatSeconds = Math.max(10, c.getInt("network-sync.heartbeat-seconds", 30));
        bossBarEnabled = c.getBoolean("bossbar.enabled", false);
        bossBarWelcomeOnJoin = c.getBoolean("bossbar.welcome-on-join", true);
        bossBarTitle = c.getString("bossbar.title", "&6&lWelcome to YaP");
        bossBarSubtitle = c.getString("bossbar.subtitle", "&7Enjoy your stay, &f{player}");
        bossBarColor = parseColor(c.getString("bossbar.color", "YELLOW"));
        bossBarDurationSeconds = Math.max(1, c.getInt("bossbar.duration-seconds", 8));
        if (header.isEmpty()) {
            header = List.of("&6&lYaP Network", "&7Welcome &f{player}");
        }
        if (footer.isEmpty()) {
            footer = List.of("&7Online: &f{online}&7/&f{max}", "&eyaplabs.us");
        }
        if (sidebar.isEmpty()) {
            sidebar = new ArrayList<>(List.of(
                    "&6&lInfo",
                    "&7Rank: &f{prefix}",
                    "&7Balance: &a{balance}",
                    "",
                    "&7World: &f{world}"));
        }
    }

    private static BossBar.Color parseColor(String raw) {
        if (raw == null || raw.isBlank()) {
            return BossBar.Color.YELLOW;
        }
        try {
            return BossBar.Color.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return BossBar.Color.YELLOW;
        }
    }

    public List<String> header() {
        return header;
    }

    public List<String> footer() {
        return footer;
    }

    public List<String> sidebar() {
        return sidebar;
    }

    public boolean sidebarEnabled() {
        return sidebarEnabled;
    }

    public boolean nametagTeams() {
        return nametagTeams;
    }

    public int refreshSeconds() {
        return refreshSeconds;
    }

    public boolean networkSyncEnabled() {
        return networkSyncEnabled;
    }

    public String serverId() {
        return serverId;
    }

    public int networkSyncHeartbeatSeconds() {
        return networkSyncHeartbeatSeconds;
    }

    public boolean bossBarEnabled() {
        return bossBarEnabled;
    }

    public boolean bossBarWelcomeOnJoin() {
        return bossBarWelcomeOnJoin;
    }

    public String bossBarTitle() {
        return bossBarTitle;
    }

    public String bossBarSubtitle() {
        return bossBarSubtitle;
    }

    public BossBar.Color bossBarColor() {
        return bossBarColor;
    }

    public int bossBarDurationSeconds() {
        return bossBarDurationSeconds;
    }
}
