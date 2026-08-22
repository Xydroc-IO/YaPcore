package com.yapcore.knobs;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/** Loads {@code knobs.yml} — Purpur-inspired encyclopedia surface. */
public final class KnobsConfig {

    public record MobKnobs(
            boolean enabled,
            boolean ridable,
            boolean controllable,
            boolean ridableInWater,
            double ridableMaxY,
            boolean takesDamageFromWater,
            boolean alwaysDropExp,
            String canPickUpLoot,
            int breedingDelayTicks,
            double maxHealth,
            double scale,
            boolean aware,
            boolean disableAi,
            boolean disableRandomStroll,
            boolean disablePanic,
            List<String> removeGoals
    ) {
    }

    private final JavaPlugin plugin;
    private FileConfiguration yaml;
    private boolean enabled = true;
    private boolean projectilesBypassMobGriefing;
    private boolean disableGiveDropping;
    private boolean anvilCumulativeCost = true;
    private int barrelRows = 3;
    private int beehiveMaxBees = 3;
    private boolean cryingObsidianPortalFrame;
    private int lightningRodRange = 128;
    private boolean bedExplode = true;
    private final Map<String, MobKnobs> mobs = new LinkedHashMap<>();

    public KnobsConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        File data = plugin.getDataFolder();
        if (!data.exists() && !data.mkdirs()) {
            plugin.getLogger().warning("Could not create " + data);
        }
        File file = new File(data, "knobs.yml");
        if (!file.exists()) {
            plugin.saveResource("knobs.yml", false);
        }
        yaml = YamlConfiguration.loadConfiguration(file);
        try (InputStream in = plugin.getResource("knobs.yml")) {
            if (in != null) {
                YamlConfiguration def = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(in, StandardCharsets.UTF_8));
                yaml.setDefaults(def);
                yaml.options().copyDefaults(true);
                yaml.save(file);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Could not merge knobs defaults", e);
        }

        enabled = yaml.getBoolean("settings.enabled", true);
        projectilesBypassMobGriefing = yaml.getBoolean("settings.projectiles-bypass-mob-griefing", false);
        disableGiveDropping = yaml.getBoolean("settings.disable-give-dropping", false);

        anvilCumulativeCost = yaml.getBoolean("blocks.anvil.cumulative-cost", true);
        barrelRows = yaml.getInt("blocks.barrel.rows", 3);
        beehiveMaxBees = yaml.getInt("blocks.beehive.max-bees-inside", 3);
        cryingObsidianPortalFrame = yaml.getBoolean("blocks.crying-obsidian.valid-for-portal-frame", false);
        lightningRodRange = yaml.getInt("blocks.lightning-rod.range", 128);
        bedExplode = yaml.getBoolean("blocks.bed.explode", true);

        mobs.clear();
        ConfigurationSection section = yaml.getConfigurationSection("mobs");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection m = section.getConfigurationSection(key);
                if (m == null) {
                    continue;
                }
                ConfigurationSection attrs = m.getConfigurationSection("attributes");
                double health = attrs != null ? attrs.getDouble("max_health", 20.0) : 20.0;
                double scale = attrs != null ? attrs.getDouble("scale", 1.0) : 1.0;
                ConfigurationSection ai = m.getConfigurationSection("ai");
                List<String> remove = ai != null ? ai.getStringList("remove-goals") : List.of();
                mobs.put(key.toLowerCase(Locale.ROOT), new MobKnobs(
                        m.getBoolean("enabled", true),
                        m.getBoolean("ridable", false),
                        m.getBoolean("controllable", true),
                        m.getBoolean("ridable-in-water", false),
                        m.getDouble("ridable-max-y", 320.0),
                        m.getBoolean("takes-damage-from-water", false),
                        m.getBoolean("always-drop-exp", false),
                        m.getString("can-pick-up-loot", "default"),
                        m.getInt("breeding-delay-ticks", 6000),
                        health,
                        scale,
                        ai == null || ai.getBoolean("aware", true),
                        ai != null && ai.getBoolean("disable-ai", false),
                        ai != null && ai.getBoolean("disable-random-stroll", false),
                        ai != null && ai.getBoolean("disable-panic", false),
                        List.copyOf(remove != null ? remove : List.of())
                ));
            }
        }
        plugin.getLogger().info("Knobs loaded — enabled=" + enabled
                + " mobs=" + mobs.size()
                + " barrelRows=" + barrelRows
                + " lightningRodRange=" + lightningRodRange);
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean projectilesBypassMobGriefing() {
        return projectilesBypassMobGriefing;
    }

    public boolean disableGiveDropping() {
        return disableGiveDropping;
    }

    public boolean anvilCumulativeCost() {
        return anvilCumulativeCost;
    }

    public int barrelRows() {
        return barrelRows;
    }

    public int beehiveMaxBees() {
        return beehiveMaxBees;
    }

    public boolean cryingObsidianPortalFrame() {
        return cryingObsidianPortalFrame;
    }

    public int lightningRodRange() {
        return lightningRodRange;
    }

    public boolean bedExplode() {
        return bedExplode;
    }

    public MobKnobs mob(String entityTypeName) {
        if (entityTypeName == null) {
            return null;
        }
        return mobs.get(entityTypeName.toLowerCase(Locale.ROOT));
    }

    public Map<String, MobKnobs> mobs() {
        return Collections.unmodifiableMap(mobs);
    }

    public FileConfiguration raw() {
        return yaml;
    }
}
