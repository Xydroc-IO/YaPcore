package com.yapcore.playerdata;

import com.yapcore.playerdata.kit.KitDef;
import com.yapcore.playerdata.kit.KitYaml;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Typed config for YaP PlayerData.
 */
public final class PlayerDataConfig {

    public record JobDef(String id, String display, Map<Material, Double> breakPays, double xpPerAction) {
    }

    private final JavaPlugin plugin;

    private String serverId = "lobby";
    private String jdbcUrl = "jdbc:mysql://127.0.0.1:3306/yap_playerdata";
    private String jdbcUser = "yap";
    private String jdbcPassword = "change-me";
    private int poolMax = 8;
    private int poolMinIdle = 2;
    private long connectionTimeoutMs = 10_000L;
    private int autosaveSeconds = 60;
    private double startingBalance = 0.0;
    private int lockTtlSeconds = 120;
    private boolean syncInventory = true;
    private boolean syncEnderchest = true;
    private boolean syncXp = true;
    private boolean syncVitals = true;
    private boolean syncEconomy = true;
    /** "global" or "server" (uses server-id). */
    private String inventoryProfileMode = "global";
    private int maxHomes = 3;
    private int mailMaxUnread = 50;
    private int auctionHours = 48;
    private int auctionFeePercent = 0;
    private Map<String, KitDef> kits = Map.of();
    private Map<String, JobDef> jobs = Map.of();

    private boolean economyEnabled = true;
    private boolean featureHomes = true;
    private boolean featureWarps = true;
    private boolean featureKits = true;
    private boolean featureMail = true;
    private boolean featureShops = true;
    private boolean featureJobs = false;
    private boolean featureAuctions = true;
    private boolean featureTraders = false;
    private boolean featureBackpack = true;
    private int backpackDefaultPages = 3;
    private int backpackMaxPages = 9;

    private boolean authEnabled = true;
    private boolean authForce = false;
    private boolean authTrustVelocity = false;
    private int authMinPasswordLength = 4;
    private int authTimeoutSeconds = 60;
    private int authMaxAttempts = 5;
    private boolean useSharedYapDb = true;

    public PlayerDataConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();
        serverId = c.getString("server-id", "lobby");
        jdbcUrl = c.getString("jdbc.url", jdbcUrl);
        jdbcUser = c.getString("jdbc.user", "yap");
        jdbcPassword = c.getString("jdbc.password", "change-me");
        poolMax = Math.max(1, c.getInt("pool.maximum-pool-size", 8));
        poolMinIdle = Math.max(0, c.getInt("pool.minimum-idle", 2));
        connectionTimeoutMs = Math.max(1000L, c.getLong("pool.connection-timeout-ms", 10_000L));
        autosaveSeconds = Math.max(5, c.getInt("autosave-seconds", 60));
        startingBalance = c.getDouble("starting-balance", 0.0);
        lockTtlSeconds = Math.max(30, c.getInt("lock-ttl-seconds", 120));
        syncInventory = c.getBoolean("sync.inventory", true);
        syncEnderchest = c.getBoolean("sync.enderchest", true);
        syncXp = c.getBoolean("sync.xp", true);
        syncVitals = c.getBoolean("sync.vitals", true);
        economyEnabled = c.getBoolean("economy.enabled", true);
        syncEconomy = economyEnabled && c.getBoolean("sync.economy", true);
        inventoryProfileMode = c.getString("inventory-profile", "global");
        maxHomes = Math.max(1, c.getInt("homes.max", 3));
        mailMaxUnread = Math.max(1, c.getInt("mail.max-unread", 50));
        auctionHours = Math.max(1, c.getInt("auctions.expire-hours", 48));
        auctionFeePercent = Math.max(0, c.getInt("auctions.fee-percent", 0));
        kits = loadAllKits(c);
        jobs = loadJobs(c.getConfigurationSection("jobs"));

        featureHomes = c.getBoolean("features.homes", true);
        featureWarps = c.getBoolean("features.warps", true);
        featureKits = c.getBoolean("features.kits", true);
        featureMail = c.getBoolean("features.mail", true);
        featureShops = economyEnabled && c.getBoolean("features.shops", true);
        featureJobs = economyEnabled && c.getBoolean("features.jobs", false);
        if (featureJobs && org.bukkit.Bukkit.getPluginManager().getPlugin("YaPSkills") != null) {
            featureJobs = false;
            plugin.getLogger().warning(
                    "features.jobs forced off — YaPSkills is loaded (use /skills, not /jobs)");
        }
        featureAuctions = economyEnabled && c.getBoolean("features.auctions", true);
        featureTraders = economyEnabled && c.getBoolean("features.traders", true);
        featureBackpack = c.getBoolean("features.backpack", true);
        backpackDefaultPages = Math.max(1, c.getInt("backpack.default-pages", 3));
        backpackMaxPages = Math.max(backpackDefaultPages, c.getInt("backpack.max-pages", 9));
        backpackMaxPages = Math.min(9, backpackMaxPages);

        authEnabled = c.getBoolean("auth.enabled", true);
        authForce = c.getBoolean("auth.force", false);
        authTrustVelocity = c.getBoolean("auth.trust-velocity", false);
        authMinPasswordLength = Math.max(1, c.getInt("auth.min-password-length", 4));
        authTimeoutSeconds = Math.max(0, c.getInt("auth.timeout-seconds", 60));
        authMaxAttempts = Math.max(1, c.getInt("auth.max-attempts", 5));
        useSharedYapDb = c.getBoolean("use-shared-yapdb", true);
    }

    private static Material parseMaterial(String name, Material fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        Material m = Material.matchMaterial(name.trim());
        return m != null ? m : fallback;
    }

    /** Resolved profile key for inv/xp/vitals on this backend. */
    public String inventoryProfile() {
        String mode = inventoryProfileMode == null ? "global" : inventoryProfileMode.trim().toLowerCase(Locale.ROOT);
        if ("server".equals(mode) || "per-server".equals(mode)) {
            return serverId;
        }
        return mode.isEmpty() ? "global" : inventoryProfileMode.trim();
    }

    /**
     * Prefer shared {@code kits.yml} (copy identically to Hub + survival).
     * Jar defaults load first; on-disk kits.yml overrides same ids and can add custom kits.
     * Missing premade kits from an old disk file are filled from the jar. Optional
     * {@code kits:} in config.yml still merge last for local overrides.
     */
    private Map<String, KitDef> loadAllKits(FileConfiguration config) {
        plugin.saveResource("kits.yml", false);
        Map<String, KitDef> merged = new LinkedHashMap<>();
        try (java.io.InputStream in = plugin.getResource("kits.yml")) {
            if (in != null) {
                FileConfiguration jar = YamlConfiguration.loadConfiguration(
                        new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
                merged.putAll(KitYaml.load(jar.getConfigurationSection("kits")));
            }
        } catch (java.io.IOException ignored) {
        }
        File kitsFile = new File(plugin.getDataFolder(), "kits.yml");
        if (kitsFile.isFile()) {
            FileConfiguration kitsYml = YamlConfiguration.loadConfiguration(kitsFile);
            merged.putAll(KitYaml.load(kitsYml.getConfigurationSection("kits")));
        }
        merged.putAll(KitYaml.load(config.getConfigurationSection("kits")));
        return Collections.unmodifiableMap(merged);
    }

    public void putKit(KitDef def) {
        Map<String, KitDef> next = new LinkedHashMap<>(kits);
        next.put(def.id(), def);
        kits = Collections.unmodifiableMap(next);
    }

    public void removeKit(String id) {
        Map<String, KitDef> next = new LinkedHashMap<>(kits);
        next.remove(id.toLowerCase(Locale.ROOT));
        kits = Collections.unmodifiableMap(next);
    }

    public void reloadKits() {
        kits = loadAllKits(plugin.getConfig());
    }

    private static Map<String, JobDef> loadJobs(ConfigurationSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<String, JobDef> out = new LinkedHashMap<>();
        for (String id : section.getKeys(false)) {
            ConfigurationSection js = section.getConfigurationSection(id);
            if (js == null) {
                continue;
            }
            String display = js.getString("display", id);
            double xp = js.getDouble("xp-per-action", 1.0);
            Map<Material, Double> pays = new HashMap<>();
            ConfigurationSection breaks = js.getConfigurationSection("break");
            if (breaks != null) {
                for (String matName : breaks.getKeys(false)) {
                    Material m = Material.matchMaterial(matName);
                    if (m != null) {
                        pays.put(m, breaks.getDouble(matName));
                    }
                }
            }
            out.put(id.toLowerCase(Locale.ROOT),
                    new JobDef(id.toLowerCase(Locale.ROOT), display, Map.copyOf(pays), xp));
        }
        return Collections.unmodifiableMap(out);
    }

    public String serverId() {
        return serverId;
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

    public int autosaveSeconds() {
        return autosaveSeconds;
    }

    public double startingBalance() {
        return startingBalance;
    }

    public int lockTtlSeconds() {
        return lockTtlSeconds;
    }

    public boolean syncInventory() {
        return syncInventory;
    }

    public boolean syncEnderchest() {
        return syncEnderchest;
    }

    public boolean syncXp() {
        return syncXp;
    }

    public boolean syncVitals() {
        return syncVitals;
    }

    public boolean syncEconomy() {
        return syncEconomy;
    }

    /** Master money switch — when false, balance/Vault/money features stay off. */
    public boolean economyEnabled() {
        return economyEnabled;
    }

    public boolean featureHomes() {
        return featureHomes;
    }

    public boolean featureWarps() {
        return featureWarps;
    }

    public boolean featureKits() {
        return featureKits;
    }

    public boolean featureMail() {
        return featureMail;
    }

    public boolean featureShops() {
        return featureShops;
    }

    public boolean featureJobs() {
        return featureJobs;
    }

    public boolean featureAuctions() {
        return featureAuctions;
    }

    public boolean featureTraders() {
        return featureTraders;
    }

    public boolean featureBackpack() {
        return featureBackpack;
    }

    public int backpackDefaultPages() {
        return backpackDefaultPages;
    }

    public int backpackMaxPages() {
        return backpackMaxPages;
    }

    public int maxHomes() {
        return maxHomes;
    }

    public int mailMaxUnread() {
        return mailMaxUnread;
    }

    public int auctionHours() {
        return auctionHours;
    }

    public int auctionFeePercent() {
        return auctionFeePercent;
    }

    public Map<String, KitDef> kits() {
        return kits;
    }

    public Map<String, JobDef> jobs() {
        return jobs;
    }

    public boolean authEnabled() {
        return authEnabled;
    }

    public boolean authForce() {
        return authForce;
    }

    public boolean authTrustVelocity() {
        return authTrustVelocity;
    }

    public int authMinPasswordLength() {
        return authMinPasswordLength;
    }

    public int authTimeoutSeconds() {
        return authTimeoutSeconds;
    }

    public int authMaxAttempts() {
        return authMaxAttempts;
    }

    public boolean useSharedYapDb() {
        return useSharedYapDb;
    }
}
