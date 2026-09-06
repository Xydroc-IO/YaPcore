package com.yapcore.conquest;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ConquestConfig {

    private final JavaPlugin plugin;
    private boolean enabled;
    private boolean useSharedYapdb = true;
    private String jdbcUrl;
    private String jdbcUser;
    private String jdbcPassword;
    private int poolMax = 6;
    private int poolMin = 1;
    private long poolTimeoutMs = 10_000;
    private int claimCost = 1;
    private boolean alliesCanBuild = true;
    private boolean enemyPvpOnly = true;
    private boolean shieldBlocksPvp = true;
    private String territoryEnterMessage;
    private String territoryLeaveMessage;
    private int mapRadius = 4;

    private boolean zonesEnabled;
    private Set<String> warzoneWorlds = Set.of();
    private Set<String> safezoneWorlds = Set.of();
    private ConquestZoneRules.Settings zoneSettings = defaultZoneSettings();
    private String zoneEnterWarzone;
    private String zoneEnterSafezone;
    private String zoneEnterWilderness;

    private boolean overclaimEnabled;
    private boolean overclaimRequireEnemy = true;

    private boolean explosionsEnabled;
    private ConquestExplosionRules.ClaimedPolicy explosionsClaimed = ConquestExplosionRules.ClaimedPolicy.DENY;

    private boolean flyEnabled;
    private boolean flyOnlyOwnTerritory = true;
    private boolean flyDisableOnLeave = true;

    private boolean combatTagEnabled;
    private int combatTagSeconds = 15;
    private boolean combatTagBlockTeleport = true;
    private boolean combatTagBlockFly = true;
    private String combatTagMessage;

    public ConquestConfig(JavaPlugin plugin) {
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
        claimCost = Math.max(1, c.getInt("power.claim-cost", 1));
        alliesCanBuild = c.getBoolean("relations.allies-can-build", true);
        enemyPvpOnly = c.getBoolean("relations.enemy-pvp-only", true);
        shieldBlocksPvp = c.getBoolean("shield.blocks-pvp", true);
        territoryEnterMessage = c.getString("territory.enter-message",
                "§7Entering §f%faction% §7conquest land.");
        territoryLeaveMessage = c.getString("territory.leave-message",
                "§7Leaving §f%faction% §7conquest land.");
        mapRadius = Math.max(1, Math.min(8, c.getInt("map.radius", 4)));

        zonesEnabled = c.getBoolean("zones.enabled", false);
        warzoneWorlds = toWorldSet(c.getStringList("zones.warzone-worlds"));
        safezoneWorlds = toWorldSet(c.getStringList("zones.safezone-worlds"));
        zoneSettings = new ConquestZoneRules.Settings(
                c.getBoolean("zones.warzone.claimable", false),
                c.getBoolean("zones.warzone.build", false),
                c.getBoolean("zones.warzone.pvp", true),
                c.getBoolean("zones.warzone.explode", true),
                c.getBoolean("zones.safezone.claimable", false),
                c.getBoolean("zones.safezone.build", false),
                c.getBoolean("zones.safezone.pvp", false),
                c.getBoolean("zones.safezone.explode", false),
                c.getBoolean("zones.wilderness.claimable", true),
                c.getBoolean("zones.wilderness.override-build", false),
                c.getBoolean("zones.wilderness.build", true),
                c.getBoolean("zones.wilderness.override-pvp", false),
                c.getBoolean("zones.wilderness.pvp", true),
                c.getBoolean("zones.wilderness.override-explode", false),
                c.getBoolean("zones.wilderness.explode", true));
        zoneEnterWarzone = c.getString("zones.enter-warzone", "§cEntering Warzone.");
        zoneEnterSafezone = c.getString("zones.enter-safezone", "§aEntering Safezone.");
        zoneEnterWilderness = c.getString("zones.enter-wilderness", "§7Entering Wilderness.");

        overclaimEnabled = c.getBoolean("overclaim.enabled", false);
        overclaimRequireEnemy = c.getBoolean("overclaim.require-enemy", true);

        explosionsEnabled = c.getBoolean("explosions.enabled", false);
        explosionsClaimed = ConquestExplosionRules.ClaimedPolicy.parse(
                c.getString("explosions.claimed", "deny"));

        flyEnabled = c.getBoolean("fly.enabled", false);
        flyOnlyOwnTerritory = c.getBoolean("fly.only-own-territory", true);
        flyDisableOnLeave = c.getBoolean("fly.disable-on-leave", true);

        combatTagEnabled = c.getBoolean("combat-tag.enabled", false);
        combatTagSeconds = Math.max(1, c.getInt("combat-tag.duration-seconds", 15));
        combatTagBlockTeleport = c.getBoolean("combat-tag.block-teleport", true);
        combatTagBlockFly = c.getBoolean("combat-tag.block-fly", true);
        combatTagMessage = c.getString("combat-tag.message",
                "§cCombat tagged for §f%seconds%s§c.");
    }

    private static Set<String> toWorldSet(List<String> raw) {
        Set<String> out = new HashSet<>();
        if (raw == null) {
            return Set.of();
        }
        for (String w : raw) {
            if (w != null && !w.isBlank()) {
                out.add(w.trim().toLowerCase(Locale.ROOT));
            }
        }
        return Set.copyOf(out);
    }

    private static ConquestZoneRules.Settings defaultZoneSettings() {
        return new ConquestZoneRules.Settings(
                false, false, true, true,
                false, false, false, false,
                true, false, true, false, true, false, true);
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

    public int claimCost() {
        return claimCost;
    }

    public boolean alliesCanBuild() {
        return alliesCanBuild;
    }

    public boolean enemyPvpOnly() {
        return enemyPvpOnly;
    }

    public boolean shieldBlocksPvp() {
        return shieldBlocksPvp;
    }

    public String territoryEnterMessage() {
        return territoryEnterMessage;
    }

    public String territoryLeaveMessage() {
        return territoryLeaveMessage;
    }

    public int mapRadius() {
        return mapRadius;
    }

    public boolean zonesEnabled() {
        return zonesEnabled;
    }

    public ConquestZoneRules.Settings zoneSettings() {
        return zoneSettings;
    }

    public String zoneEnterWarzone() {
        return zoneEnterWarzone;
    }

    public String zoneEnterSafezone() {
        return zoneEnterSafezone;
    }

    public String zoneEnterWilderness() {
        return zoneEnterWilderness;
    }

    public boolean overclaimEnabled() {
        return overclaimEnabled;
    }

    public boolean overclaimRequireEnemy() {
        return overclaimRequireEnemy;
    }

    public boolean explosionsEnabled() {
        return explosionsEnabled;
    }

    public ConquestExplosionRules.ClaimedPolicy explosionsClaimed() {
        return explosionsClaimed;
    }

    public boolean flyEnabled() {
        return flyEnabled;
    }

    public boolean flyOnlyOwnTerritory() {
        return flyOnlyOwnTerritory;
    }

    public boolean flyDisableOnLeave() {
        return flyDisableOnLeave;
    }

    public boolean combatTagEnabled() {
        return combatTagEnabled;
    }

    public int combatTagSeconds() {
        return combatTagSeconds;
    }

    public boolean combatTagBlockTeleport() {
        return combatTagBlockTeleport;
    }

    public boolean combatTagBlockFly() {
        return combatTagBlockFly;
    }

    public String combatTagMessage() {
        return combatTagMessage;
    }

    public ConquestZoneType worldDefaultZone(String world) {
        if (world == null) {
            return ConquestZoneType.WILDERNESS;
        }
        String key = world.toLowerCase(Locale.ROOT);
        if (safezoneWorlds.contains(key)) {
            return ConquestZoneType.SAFEZONE;
        }
        if (warzoneWorlds.contains(key)) {
            return ConquestZoneType.WARZONE;
        }
        return ConquestZoneType.WILDERNESS;
    }
}
