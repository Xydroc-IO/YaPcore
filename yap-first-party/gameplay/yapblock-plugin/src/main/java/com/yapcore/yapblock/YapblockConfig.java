package com.yapcore.yapblock;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.Map;

public final class YapblockConfig {

    private final JavaPlugin plugin;
    private boolean enabled = false;
    private boolean useSharedYapdb = true;
    private String jdbcUrl;
    private String jdbcUser;
    private String jdbcPassword;
    private int poolMax = 6;
    private int poolMin = 1;
    private long poolTimeoutMs = 10_000;
    private String worldName = "yapblock";
    private int gridDistance = 200;
    private int pasteY = 64;
    private int defaultSizeRadius = 50;
    private int defaultMaxMembers = 4;
    private int defaultGenTier = 0;
    private int voidY = 0;
    private int inviteExpireMinutes = 5;
    private String schematicName = "";
    private final Map<String, UpgradeTier> sizeUpgrades = new LinkedHashMap<>();
    private final Map<String, UpgradeTier> memberUpgrades = new LinkedHashMap<>();
    private final Map<String, UpgradeTier> generatorUpgrades = new LinkedHashMap<>();

    public YapblockConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();
        enabled = c.getBoolean("enabled", false);
        useSharedYapdb = c.getBoolean("use-shared-yapdb", true);
        jdbcUrl = c.getString("jdbc.url", "jdbc:mysql://127.0.0.1:3306/yap");
        jdbcUser = c.getString("jdbc.user", "yap");
        jdbcPassword = c.getString("jdbc.password", "change-me");
        poolMax = c.getInt("pool.maximum-pool-size", 6);
        poolMin = c.getInt("pool.minimum-idle", 1);
        poolTimeoutMs = c.getLong("pool.connection-timeout-ms", 10_000);
        worldName = c.getString("world.name", "yapblock");
        gridDistance = Math.max(32, c.getInt("world.grid-distance", 200));
        pasteY = c.getInt("world.paste-y", 64);
        defaultSizeRadius = Math.max(8, c.getInt("island.default-size-radius", 50));
        defaultMaxMembers = Math.max(1, c.getInt("island.default-max-members", 4));
        defaultGenTier = Math.max(0, c.getInt("island.default-gen-tier", 0));
        voidY = c.getInt("island.void-y", 0);
        inviteExpireMinutes = Math.max(1, c.getInt("invites.expire-minutes", 5));
        schematicName = c.getString("schematic.name", "");
        if (schematicName == null) {
            schematicName = "";
        } else {
            schematicName = schematicName.trim();
        }
        loadUpgrades(c.getConfigurationSection("upgrades.size"), sizeUpgrades);
        loadUpgrades(c.getConfigurationSection("upgrades.members"), memberUpgrades);
        loadUpgrades(c.getConfigurationSection("upgrades.generator"), generatorUpgrades);
        if (sizeUpgrades.isEmpty()) {
            sizeUpgrades.put("1", new UpgradeTier(1, 75, 5000));
            sizeUpgrades.put("2", new UpgradeTier(2, 100, 15000));
            sizeUpgrades.put("3", new UpgradeTier(3, 150, 40000));
        }
        if (memberUpgrades.isEmpty()) {
            memberUpgrades.put("1", new UpgradeTier(1, 6, 2500));
            memberUpgrades.put("2", new UpgradeTier(2, 8, 7500));
            memberUpgrades.put("3", new UpgradeTier(3, 12, 20000));
        }
        if (generatorUpgrades.isEmpty()) {
            generatorUpgrades.put("1", new UpgradeTier(1, 1, 3000));
            generatorUpgrades.put("2", new UpgradeTier(2, 2, 10000));
            generatorUpgrades.put("3", new UpgradeTier(3, 3, 25000));
        }
    }

    private static void loadUpgrades(ConfigurationSection section, Map<String, UpgradeTier> out) {
        out.clear();
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection tier = section.getConfigurationSection(key);
            if (tier == null) {
                continue;
            }
            int value = tier.getInt("value", 0);
            double cost = tier.getDouble("cost", 0);
            int order;
            try {
                order = Integer.parseInt(key);
            } catch (NumberFormatException e) {
                order = out.size() + 1;
            }
            out.put(key, new UpgradeTier(order, value, cost));
        }
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean useSharedYapdb() {
        return useSharedYapdb;
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

    public int poolMin() {
        return poolMin;
    }

    public long poolTimeoutMs() {
        return poolTimeoutMs;
    }

    public String worldName() {
        return worldName;
    }

    public int gridDistance() {
        return gridDistance;
    }

    public int pasteY() {
        return pasteY;
    }

    public int defaultSizeRadius() {
        return defaultSizeRadius;
    }

    public int defaultMaxMembers() {
        return defaultMaxMembers;
    }

    public int defaultGenTier() {
        return defaultGenTier;
    }

    public int voidY() {
        return voidY;
    }

    public int inviteExpireMinutes() {
        return inviteExpireMinutes;
    }

    public String schematicName() {
        return schematicName;
    }

    public Map<String, UpgradeTier> sizeUpgrades() {
        return sizeUpgrades;
    }

    public Map<String, UpgradeTier> memberUpgrades() {
        return memberUpgrades;
    }

    public Map<String, UpgradeTier> generatorUpgrades() {
        return generatorUpgrades;
    }

    public record UpgradeTier(int order, int value, double cost) {
    }
}
