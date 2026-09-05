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

/** Loads {@code knobs.yml} — YaP encyclopedia surface (Purpur-inspired, original code). */
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
            boolean retaliate,
            boolean bypassMobGriefing,
            Map<String, Double> attributes,
            boolean aware,
            boolean disableAi,
            boolean disableRandomStroll,
            boolean disablePanic,
            List<String> removeGoals,
            MobSpecials specials
    ) {
        public double attr(String key, double fallback) {
            Double v = attributes.get(key.toLowerCase(Locale.ROOT));
            return v != null ? v : fallback;
        }

        public boolean hasAttr(String key) {
            return attributes.containsKey(key.toLowerCase(Locale.ROOT));
        }
    }

    /** Per-type extras; unset fields mean vanilla / no override. */
    public record MobSpecials(
            Double creeperExplosionRadius,
            Integer creeperMaxFuseTicks,
            Boolean creeperCharged,
            Boolean phantomBurnInDaylight,
            Boolean phantomAllowGriefing,
            Boolean phantomIgnorePlayersWithTorch,
            Boolean beeCanWorkAtNight,
            Boolean beeCanWorkInRain,
            Boolean endermanAggressiveTowardsEndermites,
            Boolean wolfMilkCuresRabid,
            Double wolfSpawnRabidChance,
            Integer villagerBreedingMinDoors,
            Integer zombieReinforcementChancePct
    ) {
        static MobSpecials from(ConfigurationSection m) {
            if (m == null) {
                return empty();
            }
            return new MobSpecials(
                    m.contains("explosion-radius") ? m.getDouble("explosion-radius") : null,
                    m.contains("max-fuse-ticks") ? m.getInt("max-fuse-ticks") : null,
                    m.contains("charged") ? m.getBoolean("charged") : null,
                    m.contains("burn-in-daylight") ? m.getBoolean("burn-in-daylight") : null,
                    m.contains("allow-griefing") ? m.getBoolean("allow-griefing") : null,
                    m.contains("ignore-players-with-torch") ? m.getBoolean("ignore-players-with-torch") : null,
                    m.contains("can-work-at-night") ? m.getBoolean("can-work-at-night") : null,
                    m.contains("can-work-in-rain") ? m.getBoolean("can-work-in-rain") : null,
                    m.contains("aggressive-towards-endermites")
                            ? m.getBoolean("aggressive-towards-endermites") : null,
                    m.contains("milk-cures-rabid-wolves") ? m.getBoolean("milk-cures-rabid-wolves") : null,
                    m.contains("spawn-rabid-chance") ? m.getDouble("spawn-rabid-chance") : null,
                    m.contains("breed-min-doors") ? m.getInt("breed-min-doors") : null,
                    m.contains("reinforcement-chance-pct") && m.getInt("reinforcement-chance-pct") >= 0
                            ? m.getInt("reinforcement-chance-pct") : null
            );
        }

        static MobSpecials empty() {
            return new MobSpecials(null, null, null, null, null, null, null, null, null, null, null, null, null);
        }

        int wiredCount() {
            int n = 0;
            if (creeperExplosionRadius != null) n++;
            if (creeperMaxFuseTicks != null) n++;
            if (creeperCharged != null) n++;
            if (phantomBurnInDaylight != null) n++;
            if (phantomAllowGriefing != null) n++;
            if (phantomIgnorePlayersWithTorch != null) n++;
            if (beeCanWorkAtNight != null) n++;
            if (beeCanWorkInRain != null) n++;
            if (endermanAggressiveTowardsEndermites != null) n++;
            if (wolfMilkCuresRabid != null) n++;
            if (wolfSpawnRabidChance != null) n++;
            if (villagerBreedingMinDoors != null) n++;
            if (zombieReinforcementChancePct != null) n++;
            return n;
        }
    }

    public record GameplaySettings(
            double entityBlindnessMultiplier,
            boolean tickFluids,
            boolean useVoidDamageFix,
            boolean netheriteFireResistance,
            boolean totemWorksInVoid,
            double cropGrowthModifier,
            boolean cropGrowthNms
    ) {
    }

    private final JavaPlugin plugin;
    private FileConfiguration yaml;
    private boolean enabled = true;
    private boolean projectilesBypassMobGriefing;
    private boolean disableGiveDropping;
    private String serverModName = "YaPcore";
    private boolean anvilCumulativeCost = true;
    private int barrelRows = 3;
    private int beehiveMaxBees = 3;
    private boolean cryingObsidianPortalFrame;
    private int lightningRodRange = 128;
    private boolean bedExplode = true;
    private GameplaySettings gameplay = new GameplaySettings(1.0, true, false, false, false, 1.0, false);
    private final Map<String, MobKnobs> mobs = new LinkedHashMap<>();
    private int specialsWired;

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

        enabled = yaml.getBoolean("settings.enabled", false);
        projectilesBypassMobGriefing = yaml.getBoolean("settings.projectiles-bypass-mob-griefing", false);
        disableGiveDropping = yaml.getBoolean("settings.disable-give-dropping", false);
        serverModName = yaml.getString("settings.server-mod-name", "YaPcore");

        anvilCumulativeCost = yaml.getBoolean("blocks.anvil.cumulative-cost", true);
        barrelRows = yaml.getInt("blocks.barrel.rows", 3);
        beehiveMaxBees = yaml.getInt("blocks.beehive.max-bees-inside", 3);
        cryingObsidianPortalFrame = yaml.getBoolean("blocks.crying-obsidian.valid-for-portal-frame", false);
        lightningRodRange = yaml.getInt("blocks.lightning-rod.range", 128);
        bedExplode = yaml.getBoolean("blocks.bed.explode", true);

        gameplay = new GameplaySettings(
                yaml.getDouble("gameplay.entity-blindness-multiplier", 1.0),
                yaml.getBoolean("gameplay.tick-fluids", true),
                yaml.getBoolean("gameplay.use-void-damage-fix", false),
                yaml.getBoolean("gameplay.player.netherite-fire-resistance", false),
                yaml.getBoolean("gameplay.player.totem-of-undying-works-in-void", false),
                yaml.getDouble("gameplay.crop-growth-modifier", 1.0),
                yaml.getBoolean("gameplay.crop-growth-nms", false)
        );

        mobs.clear();
        specialsWired = 0;
        ConfigurationSection section = yaml.getConfigurationSection("mobs");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection m = section.getConfigurationSection(key);
                if (m == null) {
                    continue;
                }
                Map<String, Double> attrs = new LinkedHashMap<>();
                ConfigurationSection attrSec = m.getConfigurationSection("attributes");
                if (attrSec != null) {
                    for (String ak : attrSec.getKeys(false)) {
                        attrs.put(ak.toLowerCase(Locale.ROOT), attrSec.getDouble(ak));
                    }
                }
                ConfigurationSection ai = m.getConfigurationSection("ai");
                List<String> remove = ai != null ? ai.getStringList("remove-goals") : List.of();
                MobSpecials specials = MobSpecials.from(m);
                specialsWired += specials.wiredCount();
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
                        m.getBoolean("retaliate", true),
                        m.getBoolean("bypass-mob-griefing", false),
                        Collections.unmodifiableMap(attrs),
                        ai == null || ai.getBoolean("aware", true),
                        ai != null && ai.getBoolean("disable-ai", false),
                        ai != null && ai.getBoolean("disable-random-stroll", false),
                        ai != null && ai.getBoolean("disable-panic", false),
                        List.copyOf(remove != null ? remove : List.of()),
                        specials
                ));
            }
        }
        plugin.getLogger().info("Encyclopedia loaded — enabled=" + enabled
                + " mobs=" + mobs.size()
                + " specialsWired=" + specialsWired
                + " cropMod=" + gameplay.cropGrowthModifier()
                + " brand=" + serverModName);
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

    public String serverModName() {
        return serverModName;
    }

    public GameplaySettings gameplay() {
        return gameplay;
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

    public int specialsWired() {
        return specialsWired;
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
