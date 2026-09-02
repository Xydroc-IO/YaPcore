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

    public record GroupDef(String name, int weight, String prefix, String suffix,
                           String nameColor, String chatColor, List<String> parents) {
        public GroupDef {
            nameColor = nameColor == null ? "" : nameColor;
            chatColor = chatColor == null ? "" : chatColor;
        }
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
    private String serverContext = "";
    private boolean applyStarterPackOnFirstBoot = true;
    private Map<String, GroupDef> groups = Map.of();
    private Map<String, List<String>> tracks = Map.of();
    private Map<String, List<String>> starterGrants = Map.of();
    private Map<String, Map<String, Boolean>> editorNodes = Map.of();

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
        serverContext = c.getString("server-context", "");
        if (serverContext == null) {
            serverContext = "";
        }
        applyStarterPackOnFirstBoot = c.getBoolean("apply-starter-pack-on-first-boot", true);
        groups = loadGroups(c.getConfigurationSection("groups"));
        tracks = loadTracks(c.getConfigurationSection("tracks"));
        starterGrants = loadStarterGrants(c.getConfigurationSection("starter-grants"));
        editorNodes = loadEditorNodes(c.getConfigurationSection("editor-nodes"));
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
            String nameColor = g.getString("name-color", "");
            String chatColor = g.getString("chat-color", "");
            List<String> parents = g.getStringList("parents");
            out.put(key.toLowerCase(), new GroupDef(key.toLowerCase(), weight, prefix, suffix,
                    nameColor, chatColor, List.copyOf(parents)));
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

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Boolean>> loadEditorNodes(ConfigurationSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<String, Map<String, Boolean>> out = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            Map<String, Boolean> nodes = new LinkedHashMap<>();
            List<Map<?, ?>> rows = section.getMapList(key);
            if (!rows.isEmpty()) {
                for (Map<?, ?> row : rows) {
                    Object nodeObj = row.get("node");
                    if (nodeObj == null) {
                        continue;
                    }
                    String node = String.valueOf(nodeObj).trim();
                    if (node.isEmpty()) {
                        continue;
                    }
                    Object raw = row.get("value");
                    boolean value = raw == null || Boolean.parseBoolean(String.valueOf(raw));
                    if (raw instanceof Boolean b) {
                        value = b;
                    }
                    nodes.put(node, value);
                }
            } else {
                ConfigurationSection group = section.getConfigurationSection(key);
                if (group != null) {
                    for (String rowKey : group.getKeys(false)) {
                        ConfigurationSection row = group.getConfigurationSection(rowKey);
                        if (row != null) {
                            String node = row.getString("node", "");
                            if (!node.isBlank()) {
                                nodes.put(node, row.getBoolean("value", true));
                            }
                        }
                    }
                }
            }
            out.put(key.toLowerCase(), nodes);
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

    public String serverContext() {
        return serverContext;
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

    public Map<String, Map<String, Boolean>> editorNodes() {
        return editorNodes;
    }

    public Set<String> allGroupNames() {
        return new LinkedHashSet<>(groups.keySet());
    }
}
