package com.yapcore.tailor;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TailorConfig {

    private final JavaPlugin plugin;

    private int maxSlots = 12;
    private long cooldownMs = 3_000L;
    private int maxPngBytes = 1_048_576;
    private List<String> allowedHosts = List.of("*");
    private String skinHostPublicBaseUrl = "";
    private SkinModel defaultModel = SkinModel.WIDE;

    private boolean useSharedYapDb = true;
    private String jdbcUrl = "";
    private String jdbcUser = "yap";
    private String jdbcPassword = "change-me";
    private int poolMax = 4;
    private int poolMinIdle = 1;
    private long connectionTimeoutMs = 10_000L;
    /** When true, JE clients must send yap:presence HELLO or they are kicked. */
    private boolean requirePresenceMod = false;
    private long presenceHelloTimeoutTicks = 200L;

    public TailorConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();
        maxSlots = Math.max(1, c.getInt("max-slots", 12));
        cooldownMs = Math.max(0L, c.getLong("cooldown-ms", 3_000L));
        maxPngBytes = Math.max(16_384, c.getInt("max-png-bytes", 1_048_576));
        List<String> hosts = c.getStringList("allowed-hosts");
        if (hosts == null || hosts.isEmpty()) {
            allowedHosts = List.of("*");
        } else {
            List<String> normalized = new ArrayList<>(hosts.size());
            for (String h : hosts) {
                if (h != null && !h.isBlank()) {
                    normalized.add(h.trim().toLowerCase(Locale.ROOT));
                }
            }
            allowedHosts = normalized.isEmpty() ? List.of("*") : List.copyOf(normalized);
        }
        String base = c.getString("skin-host-public-base-url", "");
        skinHostPublicBaseUrl = base == null ? "" : base.trim();
        if (skinHostPublicBaseUrl.endsWith("/")) {
            skinHostPublicBaseUrl = skinHostPublicBaseUrl.substring(0, skinHostPublicBaseUrl.length() - 1);
        }
        defaultModel = SkinModel.fromString(c.getString("default-model", "WIDE"));

        useSharedYapDb = c.getBoolean("use-shared-yapdb", true);
        jdbcUrl = c.getString("database.jdbc-url", "");
        if (jdbcUrl == null) {
            jdbcUrl = "";
        }
        jdbcUser = c.getString("database.user", "yap");
        jdbcPassword = c.getString("database.password", "change-me");
        poolMax = Math.max(1, c.getInt("database.pool-max", 4));
        poolMinIdle = Math.max(1, c.getInt("database.pool-min-idle", 1));
        connectionTimeoutMs = Math.max(1_000L, c.getLong("database.connection-timeout-ms", 10_000L));
        requirePresenceMod = c.getBoolean("parity.bedrock-feel", false)
                || c.getBoolean("require-presence-mod", false);
        presenceHelloTimeoutTicks = Math.max(40L, c.getLong("presence-hello-timeout-ticks", 200L));
    }

    public int maxSlots() {
        return maxSlots;
    }

    public long cooldownMs() {
        return cooldownMs;
    }

    public int maxPngBytes() {
        return maxPngBytes;
    }

    public List<String> allowedHosts() {
        return allowedHosts;
    }

    public String skinHostPublicBaseUrl() {
        return skinHostPublicBaseUrl;
    }

    public SkinModel defaultModel() {
        return defaultModel;
    }

    public boolean useSharedYapDb() {
        return useSharedYapDb;
    }

    public String jdbcUrl() {
        if (jdbcUrl != null && !jdbcUrl.isBlank()) {
            return jdbcUrl;
        }
        Path db = plugin.getDataFolder().toPath().resolve("tailor.db").toAbsolutePath();
        return "jdbc:sqlite:" + db;
    }

    public String jdbcUser() {
        return jdbcUser == null ? "" : jdbcUser;
    }

    public String jdbcPassword() {
        return jdbcPassword == null ? "" : jdbcPassword;
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

    public boolean requirePresenceMod() {
        return requirePresenceMod;
    }

    public long presenceHelloTimeoutTicks() {
        return presenceHelloTimeoutTicks;
    }

    public boolean isHostAllowed(String host) {
        if (host == null || host.isBlank()) {
            return false;
        }
        String h = host.toLowerCase(Locale.ROOT);
        for (String allowed : allowedHosts) {
            if ("*".equals(allowed) || h.equals(allowed) || h.endsWith("." + allowed)) {
                return true;
            }
        }
        return false;
    }

    public String publicSkinUrl(java.util.UUID uuid, String fallbackUrl) {
        if (skinHostPublicBaseUrl != null && !skinHostPublicBaseUrl.isBlank()) {
            // Chassis ResourcePackHttpServer serves PNGs at /skin/{uuid}.png
            return skinHostPublicBaseUrl + "/skin/" + uuid + ".png";
        }
        return fallbackUrl;
    }

    public String publicCapeUrl(java.util.UUID uuid, String fallbackUrl) {
        if (skinHostPublicBaseUrl != null && !skinHostPublicBaseUrl.isBlank()) {
            return skinHostPublicBaseUrl + "/skin/" + uuid + "_cape.png";
        }
        return fallbackUrl;
    }

    /** Public URL for a saved wardrobe-slot skin PNG. */
    public String publicWardrobeSkinUrl(java.util.UUID playerUuid, long slotId) {
        if (skinHostPublicBaseUrl == null || skinHostPublicBaseUrl.isBlank()
                || playerUuid == null || slotId <= 0) {
            return null;
        }
        return skinHostPublicBaseUrl + "/skin/wardrobe/" + playerUuid + "/" + slotId + ".png";
    }

    /** Public URL for a saved wardrobe-slot cape PNG. */
    public String publicWardrobeCapeUrl(java.util.UUID playerUuid, long slotId) {
        if (skinHostPublicBaseUrl == null || skinHostPublicBaseUrl.isBlank()
                || playerUuid == null || slotId <= 0) {
            return null;
        }
        return skinHostPublicBaseUrl + "/skin/wardrobe/" + playerUuid + "/" + slotId + "_cape.png";
    }

    public boolean hasPublicSkinHost() {
        return skinHostPublicBaseUrl != null && !skinHostPublicBaseUrl.isBlank();
    }
}
