package com.yapcore.lagguard;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class LagGuardConfig {

    private final JavaPlugin plugin;
    private boolean enabled = true;
    private int maxEntitiesPerChunk = 72;
    private int maxPrimedTntPerChunk = 8;
    private int maxHopperTransfersPerWindow = 48;
    private int hopperWindowTicks = 20;
    private int maxRedstoneEventsPerWindow = 96;
    private int redstoneWindowTicks = 20;
    private int statsWriteIntervalTicks = 100;
    private boolean logTrips = true;
    private Map<String, Double> worldMultipliers = Map.of();
    private int alertTripsPerMinute = 0;
    private String alertWebhookUrl = "";
    private int entityCapItems = 40;
    private int entityCapMobs = 48;
    private int entityCapProjectiles = 24;
    private List<String> exemptRegions = List.of();
    private int maxPistonEventsPerWindow = 64;
    private int pistonWindowTicks = 20;
    private int maxObserverEventsPerWindow = 64;
    private int observerWindowTicks = 20;
    private int maxMinecartsPerChunk = 16;
    private boolean escalationEnabled = false;
    private int escalationTripsThreshold = 50;
    private int escalationWindowTicks = 200;
    private int escalationMaxItemsRemoved = 32;

    private boolean itemClearEnabled = true;
    private int itemClearIntervalSeconds = 900;
    private List<Integer> itemClearWarnSeconds = List.of(60, 30, 10);
    private boolean itemClearItems = true;
    private boolean itemClearXpOrbs = true;
    private int itemClearMinAgeTicks = 40;
    private boolean itemClearSkipNamed = false;
    private List<String> itemClearWorlds = List.of();
    private List<String> itemClearWorldBlacklist = List.of();
    private List<String> itemClearExemptRegions = List.of();
    private String itemClearWarnMessage = "&eGround items clear in &f{seconds}&e seconds.";
    private String itemClearClearMessage = "&aCleared &f{items}&a items and &f{xp}&a XP orbs.";

    public LagGuardConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();
        enabled = c.getBoolean("enabled", true);
        maxEntitiesPerChunk = Math.max(8, c.getInt("max-entities-per-chunk", 72));
        maxPrimedTntPerChunk = Math.max(1, c.getInt("max-primed-tnt-per-chunk", 8));
        maxHopperTransfersPerWindow = Math.max(1, c.getInt("max-hopper-transfers-per-window", 48));
        hopperWindowTicks = Math.max(1, c.getInt("hopper-window-ticks", 20));
        maxRedstoneEventsPerWindow = Math.max(1, c.getInt("max-redstone-events-per-window", 96));
        redstoneWindowTicks = Math.max(1, c.getInt("redstone-window-ticks", 20));
        statsWriteIntervalTicks = Math.max(20, c.getInt("stats-write-interval-ticks", 100));
        logTrips = c.getBoolean("log-trips", true);
        alertTripsPerMinute = Math.max(0, c.getInt("alert.trips-per-minute", 0));
        alertWebhookUrl = c.getString("alert.webhook-url", "");
        if (alertWebhookUrl == null) {
            alertWebhookUrl = "";
        }

        entityCapItems = Math.max(0, c.getInt("entity-caps.items", 40));
        entityCapMobs = Math.max(0, c.getInt("entity-caps.mobs", 48));
        entityCapProjectiles = Math.max(0, c.getInt("entity-caps.projectiles", 24));

        maxPistonEventsPerWindow = Math.max(0, c.getInt("max-piston-events-per-window", 64));
        pistonWindowTicks = Math.max(1, c.getInt("piston-window-ticks", 20));
        maxObserverEventsPerWindow = Math.max(0, c.getInt("max-observer-events-per-window", 64));
        observerWindowTicks = Math.max(1, c.getInt("observer-window-ticks", 20));
        maxMinecartsPerChunk = Math.max(0, c.getInt("max-minecarts-per-chunk", 16));

        escalationEnabled = c.getBoolean("escalation.enabled", false);
        escalationTripsThreshold = Math.max(0, c.getInt("escalation.trips-threshold", 50));
        escalationWindowTicks = Math.max(20, c.getInt("escalation.window-ticks", 200));
        escalationMaxItemsRemoved = Math.max(0, c.getInt("escalation.max-items-removed", 32));

        itemClearEnabled = c.getBoolean("item-clear.enabled", true);
        itemClearIntervalSeconds = Math.max(30, c.getInt("item-clear.interval-seconds", 900));
        List<Integer> warnRaw = new ArrayList<>();
        for (Object o : c.getList("item-clear.warn-seconds", List.of(60, 30, 10))) {
            if (o instanceof Number n) {
                warnRaw.add(n.intValue());
            } else if (o != null) {
                try {
                    warnRaw.add(Integer.parseInt(o.toString().trim()));
                } catch (NumberFormatException ignored) {
                    // skip
                }
            }
        }
        itemClearWarnSeconds = ItemClearPolicy.normalizeWarnSeconds(warnRaw);
        itemClearItems = c.getBoolean("item-clear.clear-items", true);
        itemClearXpOrbs = c.getBoolean("item-clear.clear-xp-orbs", true);
        itemClearMinAgeTicks = Math.max(0, c.getInt("item-clear.min-age-ticks", 40));
        itemClearSkipNamed = c.getBoolean("item-clear.skip-named", false);
        itemClearWorlds = copyStringList(c.getStringList("item-clear.worlds"));
        itemClearWorldBlacklist = copyStringList(c.getStringList("item-clear.world-blacklist"));
        itemClearExemptRegions = copyStringList(c.getStringList("item-clear.exempt-regions"));
        itemClearWarnMessage = c.getString("item-clear.warn-message",
                "&eGround items clear in &f{seconds}&e seconds.");
        itemClearClearMessage = c.getString("item-clear.clear-message",
                "&aCleared &f{items}&a items and &f{xp}&a XP orbs.");
        if (itemClearWarnMessage == null) {
            itemClearWarnMessage = "&eGround items clear in &f{seconds}&e seconds.";
        }
        if (itemClearClearMessage == null) {
            itemClearClearMessage = "&aCleared &f{items}&a items and &f{xp}&a XP orbs.";
        }

        List<String> exempt = new ArrayList<>();
        for (String name : c.getStringList("exempt-regions")) {
            if (name != null && !name.isBlank()) {
                exempt.add(name.trim());
            }
        }
        exemptRegions = List.copyOf(exempt);

        Map<String, Double> multipliers = new HashMap<>();
        ConfigurationSection section = c.getConfigurationSection("world-multipliers");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                double m = section.getDouble(key, 1.0);
                if (m > 0) {
                    multipliers.put(key.toLowerCase(Locale.ROOT), m);
                }
            }
        }
        worldMultipliers = Collections.unmodifiableMap(multipliers);
    }

    public boolean enabled() {
        return enabled;
    }

    public int maxEntitiesPerChunk() {
        return maxEntitiesPerChunk;
    }

    public int maxPrimedTntPerChunk() {
        return maxPrimedTntPerChunk;
    }

    public int maxHopperTransfersPerWindow() {
        return maxHopperTransfersPerWindow;
    }

    public int hopperWindowTicks() {
        return hopperWindowTicks;
    }

    public int maxRedstoneEventsPerWindow() {
        return maxRedstoneEventsPerWindow;
    }

    public int redstoneWindowTicks() {
        return redstoneWindowTicks;
    }

    public int statsWriteIntervalTicks() {
        return statsWriteIntervalTicks;
    }

    public boolean logTrips() {
        return logTrips;
    }

    public Map<String, Double> worldMultipliers() {
        return worldMultipliers;
    }

    /** Multiplier for a world name; defaults to 1.0. Applied to per-chunk budgets (higher = more permissive). */
    public double worldMultiplier(String world) {
        if (world == null || worldMultipliers.isEmpty()) {
            return 1.0;
        }
        return worldMultipliers.getOrDefault(world.toLowerCase(Locale.ROOT), 1.0);
    }

    public int scaled(int base, String world) {
        double m = worldMultiplier(world);
        return Math.max(1, (int) Math.round(base * m));
    }

    /** Scaled category cap; {@code 0} stays disabled (no world multiplier applied). */
    public int scaledCategoryCap(int base, String world) {
        if (base <= 0) {
            return 0;
        }
        return scaled(base, world);
    }

    public int alertTripsPerMinute() {
        return alertTripsPerMinute;
    }

    public String alertWebhookUrl() {
        return alertWebhookUrl;
    }

    public int entityCapItems() {
        return entityCapItems;
    }

    public int entityCapMobs() {
        return entityCapMobs;
    }

    public int entityCapProjectiles() {
        return entityCapProjectiles;
    }

    public int entityCapLimit(EntityCapCategory category) {
        return EntityCapPolicy.limitFor(category, entityCapItems, entityCapMobs, entityCapProjectiles);
    }

    /** Admin region names (case-insensitive match) where LagGuard spawn budgets are skipped. Soft-depend YaPRegions. */
    public List<String> exemptRegions() {
        return exemptRegions;
    }

    public boolean isExemptRegionName(String name) {
        if (name == null || exemptRegions.isEmpty()) {
            return false;
        }
        for (String exempt : exemptRegions) {
            if (exempt.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    public int maxPistonEventsPerWindow() {
        return maxPistonEventsPerWindow;
    }

    public int pistonWindowTicks() {
        return pistonWindowTicks;
    }

    public int maxObserverEventsPerWindow() {
        return maxObserverEventsPerWindow;
    }

    public int observerWindowTicks() {
        return observerWindowTicks;
    }

    public int maxMinecartsPerChunk() {
        return maxMinecartsPerChunk;
    }

    public boolean escalationEnabled() {
        return escalationEnabled;
    }

    public int escalationTripsThreshold() {
        return escalationTripsThreshold;
    }

    public int escalationWindowTicks() {
        return escalationWindowTicks;
    }

    public int escalationMaxItemsRemoved() {
        return escalationMaxItemsRemoved;
    }

    public boolean itemClearEnabled() {
        return itemClearEnabled;
    }

    public int itemClearIntervalSeconds() {
        return itemClearIntervalSeconds;
    }

    public List<Integer> itemClearWarnSeconds() {
        return itemClearWarnSeconds;
    }

    public boolean itemClearItems() {
        return itemClearItems;
    }

    public boolean itemClearXpOrbs() {
        return itemClearXpOrbs;
    }

    public int itemClearMinAgeTicks() {
        return itemClearMinAgeTicks;
    }

    public boolean itemClearSkipNamed() {
        return itemClearSkipNamed;
    }

    public List<String> itemClearWorlds() {
        return itemClearWorlds;
    }

    public List<String> itemClearWorldBlacklist() {
        return itemClearWorldBlacklist;
    }

    public List<String> itemClearExemptRegions() {
        return itemClearExemptRegions;
    }

    public String itemClearWarnMessage() {
        return itemClearWarnMessage;
    }

    public String itemClearClearMessage() {
        return itemClearClearMessage;
    }

    private static List<String> copyStringList(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String s : raw) {
            if (s != null && !s.isBlank()) {
                out.add(s.trim());
            }
        }
        return List.copyOf(out);
    }
}
