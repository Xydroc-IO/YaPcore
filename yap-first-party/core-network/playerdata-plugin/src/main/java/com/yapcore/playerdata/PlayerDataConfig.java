package com.yapcore.playerdata;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Typed config for YaP PlayerData.
 */
public final class PlayerDataConfig {

    public record KitDef(String id, long delaySeconds, List<ItemStack> items) {
    }

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
    private boolean featureShops = false;
    private boolean featureJobs = false;
    private boolean featureAuctions = false;
    private boolean featureClaims = true;
    private boolean featureTraders = false;

    private boolean claimsEnabled = true;
    private boolean claimsRequireClaimToBuild = false;
    private Material claimsTool = Material.GOLDEN_SHOVEL;
    private Material claimsInspectTool = Material.STICK;
    private int claimsStartingBlocks = 2500;
    private int claimsBlocksPerHour = 100;
    private int claimsMinArea = 9;
    private int claimsMaxArea = 50_000;
    private int claimsVisualSeconds = 8;
    private int claimsSubMinArea = 4;
    private boolean claimsTaxEnabled = true;
    private double claimsTaxPerBlockPerDay = 0.01;
    private int claimsTaxTickMinutes = 60;
    private double claimsTaxFreezeAmount = 50.0;
    private double claimsTaxAbandonAmount = 200.0;
    private final EnumMap<com.yapcore.regions.RegionFlag, com.yapcore.regions.FlagValue> claimDefaultFlags =
            new EnumMap<>(com.yapcore.regions.RegionFlag.class);

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
        kits = loadKits(c.getConfigurationSection("kits"));
        jobs = loadJobs(c.getConfigurationSection("jobs"));

        featureHomes = c.getBoolean("features.homes", true);
        featureWarps = c.getBoolean("features.warps", true);
        featureKits = c.getBoolean("features.kits", true);
        featureMail = c.getBoolean("features.mail", true);
        featureShops = economyEnabled && c.getBoolean("features.shops", false);
        featureJobs = economyEnabled && c.getBoolean("features.jobs", false);
        featureAuctions = economyEnabled && c.getBoolean("features.auctions", false);
        featureTraders = economyEnabled && c.getBoolean("features.traders", false);
        // features.claims AND legacy claims.enabled
        featureClaims = c.getBoolean("features.claims", true) && c.getBoolean("claims.enabled", true);

        claimsEnabled = featureClaims;
        claimsRequireClaimToBuild = c.getBoolean("claims.require-claim-to-build", false);
        claimsTool = parseMaterial(c.getString("claims.tool", "GOLDEN_SHOVEL"), Material.GOLDEN_SHOVEL);
        claimsInspectTool = parseMaterial(c.getString("claims.inspect-tool", "STICK"), Material.STICK);
        claimsStartingBlocks = Math.max(0, c.getInt("claims.starting-blocks", 2500));
        claimsBlocksPerHour = Math.max(0, c.getInt("claims.blocks-per-hour", 100));
        claimsMinArea = Math.max(1, c.getInt("claims.min-area", 9));
        claimsMaxArea = Math.max(claimsMinArea, c.getInt("claims.max-area", 50_000));
        claimsVisualSeconds = Math.max(1, c.getInt("claims.visual-seconds", 8));
        claimsSubMinArea = Math.max(1, c.getInt("claims.subdivide-min-area", 4));
        claimsTaxEnabled = economyEnabled && featureClaims && c.getBoolean("claims.tax.enabled", true);
        claimsTaxPerBlockPerDay = Math.max(0, c.getDouble("claims.tax.per-block-per-day", 0.01));
        claimsTaxTickMinutes = Math.max(1, c.getInt("claims.tax.tick-minutes", 60));
        claimsTaxFreezeAmount = Math.max(0, c.getDouble("claims.tax.freeze-at", 50.0));
        claimsTaxAbandonAmount = Math.max(claimsTaxFreezeAmount, c.getDouble("claims.tax.abandon-at", 200.0));
        loadClaimDefaultFlags(c.getConfigurationSection("claims.default-flags"));

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

    private static Map<String, KitDef> loadKits(ConfigurationSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<String, KitDef> out = new LinkedHashMap<>();
        for (String id : section.getKeys(false)) {
            ConfigurationSection ks = section.getConfigurationSection(id);
            if (ks == null) {
                continue;
            }
            long delay = ks.getLong("delay-seconds", 86400);
            List<ItemStack> items = new ArrayList<>();
            List<?> raw = ks.getList("items");
            if (raw != null) {
                for (Object o : raw) {
                    if (o instanceof Map<?, ?> map) {
                        String mat = String.valueOf(map.get("material"));
                        int amount = map.get("amount") instanceof Number n ? n.intValue() : 1;
                        Material material = Material.matchMaterial(mat);
                        if (material != null && material.isItem()) {
                            items.add(new ItemStack(material, Math.max(1, amount)));
                        }
                    }
                }
            }
            out.put(id.toLowerCase(Locale.ROOT), new KitDef(id.toLowerCase(Locale.ROOT), delay, List.copyOf(items)));
        }
        return Collections.unmodifiableMap(out);
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

    public boolean featureClaims() {
        return featureClaims;
    }

    public boolean featureTraders() {
        return featureTraders;
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

    public boolean claimsEnabled() {
        return claimsEnabled;
    }

    public boolean claimsRequireClaimToBuild() {
        return claimsRequireClaimToBuild;
    }

    public Material claimsTool() {
        return claimsTool;
    }

    public Material claimsInspectTool() {
        return claimsInspectTool;
    }

    public int claimsStartingBlocks() {
        return claimsStartingBlocks;
    }

    public int claimsBlocksPerHour() {
        return claimsBlocksPerHour;
    }

    public int claimsMinArea() {
        return claimsMinArea;
    }

    public int claimsMaxArea() {
        return claimsMaxArea;
    }

    public int claimsVisualSeconds() {
        return claimsVisualSeconds;
    }

    public int claimsSubMinArea() {
        return claimsSubMinArea;
    }

    public boolean claimsTaxEnabled() {
        return claimsTaxEnabled;
    }

    public double claimsTaxPerBlockPerDay() {
        return claimsTaxPerBlockPerDay;
    }

    public int claimsTaxTickMinutes() {
        return claimsTaxTickMinutes;
    }

    public double claimsTaxFreezeAmount() {
        return claimsTaxFreezeAmount;
    }

    public double claimsTaxAbandonAmount() {
        return claimsTaxAbandonAmount;
    }

    public com.yapcore.regions.FlagValue defaultClaimFlag(com.yapcore.regions.RegionFlag flag) {
        return claimDefaultFlags.getOrDefault(flag, switch (flag) {
            case PVP, FIRE_SPREAD -> com.yapcore.regions.FlagValue.DENY;
            default -> com.yapcore.regions.FlagValue.ALLOW;
        });
    }

    private void loadClaimDefaultFlags(ConfigurationSection section) {
        claimDefaultFlags.clear();
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            com.yapcore.regions.RegionFlag.parse(key).ifPresent(flag ->
                    claimDefaultFlags.put(flag, com.yapcore.regions.FlagValue.parse(section.getString(key))));
        }
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
