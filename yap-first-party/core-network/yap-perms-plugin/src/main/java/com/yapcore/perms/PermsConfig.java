package com.yapcore.perms;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PermsConfig {

    public record GroupDef(String name, int weight, String prefix, String suffix, List<String> parents) {
    }

    private final JavaPlugin plugin;
    private boolean useSharedYapDb = true;
    private String jdbcUrl = "jdbc:mysql://127.0.0.1:3306/yap_playerdata";
    private String jdbcUser = "yap";
    private String jdbcPassword = "change-me";
    private int poolMax = 8;
    private int poolMinIdle = 2;
    private long connectionTimeoutMs = 10_000L;
    private String defaultGroup = "default";
    private String defaultTrack = "yap";
    private boolean applyStarterPackOnFirstBoot = true;
    private Map<String, GroupDef> groups = Map.of();
    private Map<String, List<String>> tracks = Map.of();
    private Map<String, List<String>> starterGrants = Map.of();

    public PermsConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();
        useSharedYapDb = c.getBoolean("use-shared-yapdb", true);
        jdbcUrl = c.getString("jdbc.url", jdbcUrl);
        jdbcUser = c.getString("jdbc.user", jdbcUser);
        jdbcPassword = c.getString("jdbc.password", jdbcPassword);
        poolMax = Math.max(1, c.getInt("pool.maximum-pool-size", 8));
        poolMinIdle = Math.max(0, c.getInt("pool.minimum-idle", 2));
        connectionTimeoutMs = Math.max(1000L, c.getLong("pool.connection-timeout-ms", 10_000L));
        defaultGroup = c.getString("default-group", "default");
        defaultTrack = c.getString("default-track", "yap");
        applyStarterPackOnFirstBoot = c.getBoolean("apply-starter-pack-on-first-boot", true);
        groups = loadGroups(c.getConfigurationSection("groups"));
        tracks = loadTracks(c.getConfigurationSection("tracks"));
        starterGrants = loadStarterGrants(c.getConfigurationSection("starter-grants"));
    }

    private static Map<String, GroupDef> loadGroups(ConfigurationSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<String, GroupDef> out = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            ConfigurationSection g = section.getConfigurationSection(key);
            if (g == null) {
                continue;
            }
            int weight = g.getInt("weight", 0);
            String prefix = g.getString("prefix", "");
            String suffix = g.getString("suffix", "");
            List<String> parents = g.getStringList("parents");
            out.put(key.toLowerCase(), new GroupDef(key.toLowerCase(), weight, prefix, suffix, List.copyOf(parents)));
        }
        return Collections.unmodifiableMap(out);
    }

    private static Map<String, List<String>> loadTracks(ConfigurationSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            List<String> groups = section.getStringList(key);
            List<String> normalized = new ArrayList<>();
            for (String g : groups) {
                normalized.add(g.toLowerCase());
            }
            out.put(key.toLowerCase(), List.copyOf(normalized));
        }
        return Collections.unmodifiableMap(out);
    }

    private static Map<String, List<String>> loadStarterGrants(ConfigurationSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            out.put(key.toLowerCase(), List.copyOf(section.getStringList(key)));
        }
        return Collections.unmodifiableMap(out);
    }

    public boolean useSharedYapDb() {
        return useSharedYapDb;
    }

    public String jdbcUrl() {
        return jdbcUrl;
    }

    public String jdbcUser() {
        return jdbcUser;
    }

    public String jdbcPassword() {
        return jdbcPassword;
    }

    public int poolMax() {
        return poolMax;
    }

    public int poolMinIdle() {
        return poolMinIdle;
    }

    public long connectionTimeoutMs() {
        return connectionTimeoutMs;
    }

    public String defaultGroup() {
        return defaultGroup;
    }

    public String defaultTrack() {
        return defaultTrack;
    }

    public boolean applyStarterPackOnFirstBoot() {
        return applyStarterPackOnFirstBoot;
    }

    public Map<String, GroupDef> groups() {
        return groups;
    }

    public Map<String, List<String>> tracks() {
        return tracks;
    }

    public Map<String, List<String>> starterGrants() {
        return starterGrants;
    }

    public Set<String> allGroupNames() {
        return new LinkedHashSet<>(groups.keySet());
    }
}
