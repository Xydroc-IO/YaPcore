package com.yapcore.regions;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class RegionsConfig {

    private final JavaPlugin plugin;
    private String serverId = "default";

    private boolean notifyEnabled = true;
    private boolean notifyTitle = true;
    private boolean notifyActionBar = true;
    private boolean notifyChat = false;
    private int notifyFadeInMs = 200;
    private int notifyStayMs = 1800;
    private int notifyFadeOutMs = 400;
    private String notifyEnterTitle = "&aEntering";
    private String notifyEnterSubtitle = "&f{region}";
    private String notifyLeaveTitle = "&7Leaving";
    private String notifyLeaveSubtitle = "&f{region}";
    private String notifyActionBarEnter = "&a» &f{region}";
    private String notifyActionBarLeave = "&7« &f{region}";
    /** When set, players outside any region gamemode override use this mode. */
    private org.bukkit.GameMode outsideGameMode;

    /** Backends where hunger + all player damage are denied everywhere (lobby / hub). */
    private Set<String> safeServers = Set.of("lobby", "hub");

    private boolean spawnPadEnabled = true;
    private String spawnPadRegionName = "spawn";
    private int spawnPadRadius = 64;
    private int spawnPadHeight = 96;

    public RegionsConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Fixed server id for unit tests (no Bukkit plugin). */
    public RegionsConfig(String serverId) {
        this.plugin = null;
        this.serverId = serverId == null || serverId.isBlank() ? "default" : serverId.trim();
    }

    public void reload() {
        if (plugin == null) {
            return;
        }
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();
        String configured = c.getString("server-id", "default");
        String hinted = readInstanceServerId();
        serverId = resolveServerId(configured, hinted);
        if (hinted != null && !hinted.isBlank()
                && (configured == null || !serverId.equalsIgnoreCase(configured.trim()))) {
            plugin.getLogger().info("YaPRegions server-id " + configured + " → " + serverId
                    + " (fleet yap-server-id.txt)");
        }

        notifyEnabled = c.getBoolean("notify.enabled", true);
        notifyTitle = c.getBoolean("notify.title", true);
        notifyActionBar = c.getBoolean("notify.action-bar", true);
        notifyChat = c.getBoolean("notify.chat", false);
        notifyFadeInMs = Math.max(0, c.getInt("notify.fade-in-ms", 200));
        notifyStayMs = Math.max(200, c.getInt("notify.stay-ms", 1800));
        notifyFadeOutMs = Math.max(0, c.getInt("notify.fade-out-ms", 400));
        notifyEnterTitle = c.getString("notify.enter-title", "&aEntering");
        notifyEnterSubtitle = c.getString("notify.enter-subtitle", "&f{region}");
        notifyLeaveTitle = c.getString("notify.leave-title", "&7Leaving");
        notifyLeaveSubtitle = c.getString("notify.leave-subtitle", "&f{region}");
        notifyActionBarEnter = c.getString("notify.action-bar-enter", "&a» &f{region}");
        notifyActionBarLeave = c.getString("notify.action-bar-leave", "&7« &f{region}");
        outsideGameMode = parseGameMode(c.getString("outside-gamemode", ""));

        safeServers = new HashSet<>();
        for (String id : c.getStringList("safe-servers")) {
            if (id != null && !id.isBlank()) {
                safeServers.add(id.trim().toLowerCase(Locale.ROOT));
            }
        }
        if (safeServers.isEmpty() && !c.isSet("safe-servers")) {
            // Product default when key omitted: lobby/hub are always safe.
            safeServers = Set.of("lobby", "hub");
        }

        spawnPadEnabled = c.getBoolean("spawn-pad.enabled", true);
        spawnPadRegionName = c.getString("spawn-pad.region-name", "spawn");
        if (spawnPadRegionName == null || spawnPadRegionName.isBlank()) {
            spawnPadRegionName = "spawn";
        } else {
            spawnPadRegionName = spawnPadRegionName.trim().toLowerCase(Locale.ROOT);
        }
        spawnPadRadius = Math.max(8, c.getInt("spawn-pad.radius", 64));
        spawnPadHeight = Math.max(16, c.getInt("spawn-pad.height", 96));
    }

    private static org.bukkit.GameMode parseGameMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return org.bukkit.GameMode.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Fleet stamps {@code yap-server-id.txt}. A copied seed YAML often stays {@code default}
     * (or the wrong backend), which loads zero regions and skips spawn flags.
     */
    static String resolveServerId(String configured, String hinted) {
        if (hinted != null) {
            String line = firstLine(hinted);
            if (!line.isEmpty()) {
                return line;
            }
        }
        if (configured == null || configured.isBlank()) {
            return "default";
        }
        return configured.trim();
    }

    /**
     * {@code plugins/YaPRegions} → instance root. Must absolutize: a relative data folder
     * has a null grandparent, so the hint is skipped.
     */
    static Path instanceServerIdHint(Path dataFolder) {
        if (dataFolder == null) {
            return null;
        }
        Path abs = dataFolder.toAbsolutePath().normalize();
        Path plugins = abs.getParent();
        if (plugins == null) {
            return null;
        }
        Path instance = plugins.getParent();
        if (instance == null) {
            return null;
        }
        return instance.resolve("yap-server-id.txt");
    }

    private String readInstanceServerId() {
        try {
            Path hint = instanceServerIdHint(plugin.getDataFolder().toPath());
            if (hint != null && Files.isRegularFile(hint)) {
                return firstLine(Files.readString(hint));
            }
        } catch (Exception ignored) {
            // keep YAML value
        }
        return null;
    }

    private static String firstLine(String raw) {
        if (raw == null) {
            return "";
        }
        String line = raw.trim();
        int nl = line.indexOf('\n');
        if (nl >= 0) {
            line = line.substring(0, nl).trim();
        }
        int cr = line.indexOf('\r');
        if (cr >= 0) {
            line = line.substring(0, cr).trim();
        }
        return line;
    }

    public String serverId() {
        return serverId;
    }

    public boolean notifyEnabled() {
        return notifyEnabled;
    }

    public boolean notifyTitle() {
        return notifyTitle;
    }

    public boolean notifyActionBar() {
        return notifyActionBar;
    }

    public boolean notifyChat() {
        return notifyChat;
    }

    public int notifyFadeInMs() {
        return notifyFadeInMs;
    }

    public int notifyStayMs() {
        return notifyStayMs;
    }

    public int notifyFadeOutMs() {
        return notifyFadeOutMs;
    }

    public String notifyEnterTitle() {
        return notifyEnterTitle;
    }

    public String notifyEnterSubtitle() {
        return notifyEnterSubtitle;
    }

    public String notifyLeaveTitle() {
        return notifyLeaveTitle;
    }

    public String notifyLeaveSubtitle() {
        return notifyLeaveSubtitle;
    }

    public String notifyActionBarEnter() {
        return notifyActionBarEnter;
    }

    public String notifyActionBarLeave() {
        return notifyActionBarLeave;
    }

    /** Null when unset — leave restores previous mode (legacy). */
    public org.bukkit.GameMode outsideGameMode() {
        return outsideGameMode;
    }

    /** True when this backend denies hunger + all damage everywhere (e.g. lobby). */
    public boolean isSafeServer() {
        return safeServers.contains(serverId.toLowerCase(Locale.ROOT));
    }

    public Set<String> safeServers() {
        return safeServers;
    }

    public boolean spawnPadEnabled() {
        return spawnPadEnabled;
    }

    public String spawnPadRegionName() {
        return spawnPadRegionName;
    }

    public int spawnPadRadius() {
        return spawnPadRadius;
    }

    public int spawnPadHeight() {
        return spawnPadHeight;
    }

    /** Unit-test harness. */
    void applySafeServersForTest(Set<String> ids) {
        if (ids == null || ids.isEmpty()) {
            this.safeServers = Set.of();
        } else {
            Set<String> next = new HashSet<>();
            for (String id : ids) {
                if (id != null && !id.isBlank()) {
                    next.add(id.trim().toLowerCase(Locale.ROOT));
                }
            }
            this.safeServers = Set.copyOf(next);
        }
    }
}
