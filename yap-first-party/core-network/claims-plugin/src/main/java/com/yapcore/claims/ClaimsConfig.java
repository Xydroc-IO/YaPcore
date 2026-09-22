package com.yapcore.claims;

import com.yapcore.regions.FlagValue;
import com.yapcore.regions.RegionFlag;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Typed config for YaPClaims. */
public final class ClaimsConfig {

    public enum ClaimMode {
        CHUNK,
        FREEFORM;

        static ClaimMode parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return CHUNK;
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "freeform", "corners", "selection", "area" -> FREEFORM;
                default -> CHUNK;
            };
        }
    }

    private final JavaPlugin plugin;

    private String serverId = "lobby";
    private String jdbcUrl = "jdbc:mysql://127.0.0.1:3306/yap_playerdata?useSSL=false&allowPublicKeyRetrieval=true";
    private String jdbcUser = "yap";
    private String jdbcPassword = "change-me";
    private int poolMax = 8;
    private int poolMinIdle = 2;
    private long connectionTimeoutMs = 10_000L;
    private boolean useSharedYapDb = true;

    private boolean claimsEnabled = true;
    private boolean claimsRequireClaimToBuild = false;
    private Material claimsTool = Material.GOLDEN_SHOVEL;
    private Material claimsInspectTool = Material.STICK;
    /** chunk = one shovel click claims the 16×16 chunk; freeform = two-corner selection. */
    private ClaimMode claimsMode = ClaimMode.CHUNK;
    /** When false, no claim-block budget — limits use {@link #claimsMaxClaims}. */
    private boolean claimsUseClaimBlocks = false;
    private int claimsStartingBlocks = 2500;
    private int claimsBlocksPerHour = 0;
    private int claimsMaxClaims = 25;
    private int claimsMinArea = 9;
    private int claimsMaxArea = 50_000;
    private int claimsVisualSeconds = 8;
    private int claimsSubMinArea = 4;
    private boolean claimsCostEnabled = true;
    private double claimsCostAmount = 10000.0;
    private boolean claimsCostRefundOnAbandon = false;
    private double claimsCostRefundPercent = 0.0;
    /** Horizontal plot edge length (default 64 → 64×64 claim). */
    private int claimsPlotSize = 64;
    /** Blocks below the claim standing Y that stay protected. */
    private int claimsPlotDepth = 64;
    private boolean claimsTaxEnabled = false;
    private double claimsTaxPerBlockPerDay = 0.01;
    private Set<String> claimsDenyWorlds = Set.of();
    private int claimsTaxTickMinutes = 60;
    private double claimsTaxFreezeAmount = 50.0;
    private double claimsTaxAbandonAmount = 200.0;
    private final EnumMap<RegionFlag, FlagValue> claimDefaultFlags = new EnumMap<>(RegionFlag.class);

    public ClaimsConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Fixed server id for unit tests (no Bukkit plugin). */
    public ClaimsConfig(String serverId) {
        this.plugin = null;
        this.serverId = serverId == null || serverId.isBlank() ? "lobby" : serverId.trim();
    }

    public void reload() {
        if (plugin == null) {
            return;
        }
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();
        String configured = c.getString("server-id", "lobby");
        String hinted = readInstanceServerId();
        serverId = resolveServerId(configured, hinted);
        if (hinted != null && !hinted.isBlank()
                && (configured == null || !serverId.equalsIgnoreCase(configured.trim()))) {
            plugin.getLogger().info("YaPClaims server-id " + configured + " → " + serverId
                    + " (fleet yap-server-id.txt)");
        }

        jdbcUrl = c.getString("jdbc.url", jdbcUrl);
        jdbcUser = c.getString("jdbc.user", "yap");
        jdbcPassword = c.getString("jdbc.password", "change-me");
        poolMax = Math.max(1, c.getInt("pool.maximum-pool-size", 8));
        poolMinIdle = Math.max(0, c.getInt("pool.minimum-idle", 2));
        connectionTimeoutMs = Math.max(1000L, c.getLong("pool.connection-timeout-ms", 10_000L));
        useSharedYapDb = c.getBoolean("use-shared-yapdb", true);

        claimsEnabled = c.getBoolean("claims.enabled", true);
        claimsRequireClaimToBuild = c.getBoolean("claims.require-claim-to-build", false);
        claimsTool = parseMaterial(c.getString("claims.tool", "GOLDEN_SHOVEL"), Material.GOLDEN_SHOVEL);
        claimsInspectTool = parseMaterial(c.getString("claims.inspect-tool", "STICK"), Material.STICK);
        claimsMode = ClaimMode.parse(c.getString("claims.mode", "chunk"));
        claimsUseClaimBlocks = c.getBoolean("claims.use-claim-blocks", false);
        claimsStartingBlocks = Math.max(0, c.getInt("claims.starting-blocks", 2500));
        claimsBlocksPerHour = Math.max(0, c.getInt("claims.blocks-per-hour", 0));
        claimsMaxClaims = Math.max(1, c.getInt("claims.max-claims", 25));
        claimsMinArea = Math.max(1, c.getInt("claims.min-area", 9));
        claimsMaxArea = Math.max(claimsMinArea, c.getInt("claims.max-area", 50_000));
        claimsVisualSeconds = Math.max(1, c.getInt("claims.visual-seconds", 8));
        claimsSubMinArea = Math.max(1, c.getInt("claims.subdivide-min-area", 4));
        claimsCostEnabled = c.getBoolean("claims.cost.enabled", true);
        claimsCostAmount = Math.max(0, c.getDouble("claims.cost.amount", 10000.0));
        claimsCostRefundOnAbandon = c.getBoolean("claims.cost.refund-on-abandon", false);
        claimsCostRefundPercent = Math.max(0, Math.min(100,
                c.getDouble("claims.cost.refund-percent", 0.0)));
        claimsPlotSize = Math.max(1, c.getInt("claims.plot-size", 64));
        claimsPlotDepth = Math.max(0, c.getInt("claims.plot-depth", 64));
        claimsTaxEnabled = claimsEnabled && c.getBoolean("claims.tax.enabled", false);
        claimsTaxPerBlockPerDay = Math.max(0, c.getDouble("claims.tax.per-block-per-day", 0.01));
        claimsTaxTickMinutes = Math.max(1, c.getInt("claims.tax.tick-minutes", 60));
        claimsTaxFreezeAmount = Math.max(0, c.getDouble("claims.tax.freeze-at", 50.0));
        claimsTaxAbandonAmount = Math.max(claimsTaxFreezeAmount, c.getDouble("claims.tax.abandon-at", 200.0));
        loadClaimDefaultFlags(c.getConfigurationSection("claims.default-flags"));
        Set<String> deny = new HashSet<>();
        for (String w : c.getStringList("claims.deny-worlds")) {
            if (w != null && !w.isBlank()) {
                deny.add(w.trim().toLowerCase(Locale.ROOT));
            }
        }
        claimsDenyWorlds = deny.isEmpty() ? Set.of() : Set.copyOf(deny);
    }

    static String resolveServerId(String configured, String hinted) {
        if (hinted != null) {
            String line = firstLine(hinted);
            if (!line.isEmpty()) {
                return line;
            }
        }
        if (configured == null || configured.isBlank()) {
            return "lobby";
        }
        return configured.trim();
    }

    private String readInstanceServerId() {
        try {
            Path data = plugin.getDataFolder().toPath().toAbsolutePath().normalize();
            Path root = data.getParent() != null && data.getParent().getParent() != null
                    ? data.getParent().getParent()
                    : data.getParent();
            if (root == null) {
                return null;
            }
            Path stamp = root.resolve("yap-server-id.txt");
            if (!Files.isRegularFile(stamp)) {
                return null;
            }
            return Files.readString(stamp);
        } catch (Exception e) {
            return null;
        }
    }

    private static String firstLine(String raw) {
        if (raw == null) {
            return "";
        }
        for (String line : raw.split("\\R")) {
            String t = line.trim();
            if (!t.isEmpty() && !t.startsWith("#")) {
                return t;
            }
        }
        return "";
    }

    private static Material parseMaterial(String raw, Material fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Material.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private void loadClaimDefaultFlags(ConfigurationSection section) {
        claimDefaultFlags.clear();
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            RegionFlag.parse(key).ifPresent(flag ->
                    claimDefaultFlags.put(flag, FlagValue.parse(section.getString(key))));
        }
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

    public boolean useSharedYapDb() {
        return useSharedYapDb;
    }

    public boolean claimsEnabled() {
        return claimsEnabled;
    }

    /** Alias kept for callers that previously checked {@code features.claims}. */
    public boolean featureClaims() {
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

    public ClaimMode claimsMode() {
        return claimsMode;
    }

    public boolean claimsUseClaimBlocks() {
        return claimsUseClaimBlocks;
    }

    public int claimsStartingBlocks() {
        return claimsStartingBlocks;
    }

    public int claimsBlocksPerHour() {
        return claimsBlocksPerHour;
    }

    public int claimsMaxClaims() {
        return claimsMaxClaims;
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

    public boolean claimsCostEnabled() {
        return claimsCostEnabled && claimsCostAmount > 0;
    }

    public double claimsCostAmount() {
        return claimsCostAmount;
    }

    public boolean claimsCostRefundOnAbandon() {
        return claimsCostRefundOnAbandon && claimsCostRefundPercent > 0;
    }

    public double claimsCostRefundPercent() {
        return claimsCostRefundPercent;
    }

    public int claimsPlotSize() {
        return claimsPlotSize;
    }

    public int claimsPlotDepth() {
        return claimsPlotDepth;
    }

    public Set<String> claimsDenyWorlds() {
        return claimsDenyWorlds;
    }

    public boolean claimsWorldDenied(String worldName) {
        if (worldName == null || claimsDenyWorlds.isEmpty()) {
            return false;
        }
        return claimsDenyWorlds.contains(worldName.trim().toLowerCase(Locale.ROOT));
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

    public FlagValue defaultClaimFlag(RegionFlag flag) {
        return claimDefaultFlags.getOrDefault(flag, switch (flag) {
            case PVP, FIRE_SPREAD, TNT, CREEPER_EXPLOSION -> FlagValue.DENY;
            default -> FlagValue.ALLOW;
        });
    }
}
