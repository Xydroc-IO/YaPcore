package com.yapcore.yap420;

import com.yapcore.yap420.market.MarketSettings;
import com.yapcore.yap420.plant.StrainId;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Typed YaP420 settings. */
public final class Yap420Config {

    private final boolean enabled;
    private final long growthPeriodTicks;
    private final double advanceChance;
    private final int stages;
    private final int minLight;
    private final boolean requireWater;
    private final int waterRadius;
    private final Set<Material> soils;
    private final Set<Material> growthSoils;
    private final double herbalismXp;
    private final long curePeriodTicks;
    private final long cureMillisPerBud;
    private final int rackMaxSlots;
    private final String hazeChannel;
    private final double hazeIntensity;
    private final int hazeDurationTicks;
    private final Wild wild;
    private final MarketSettings market;
    private final Messages messages;

    private Yap420Config(
            boolean enabled,
            long growthPeriodTicks,
            double advanceChance,
            int stages,
            int minLight,
            boolean requireWater,
            int waterRadius,
            Set<Material> soils,
            Set<Material> growthSoils,
            double herbalismXp,
            long curePeriodTicks,
            long cureMillisPerBud,
            int rackMaxSlots,
            String hazeChannel,
            double hazeIntensity,
            int hazeDurationTicks,
            Wild wild,
            MarketSettings market,
            Messages messages
    ) {
        this.enabled = enabled;
        this.growthPeriodTicks = growthPeriodTicks;
        this.advanceChance = advanceChance;
        this.stages = stages;
        this.minLight = minLight;
        this.requireWater = requireWater;
        this.waterRadius = waterRadius;
        this.soils = soils;
        this.growthSoils = growthSoils;
        this.herbalismXp = herbalismXp;
        this.curePeriodTicks = curePeriodTicks;
        this.cureMillisPerBud = cureMillisPerBud;
        this.rackMaxSlots = rackMaxSlots;
        this.hazeChannel = hazeChannel;
        this.hazeIntensity = hazeIntensity;
        this.hazeDurationTicks = hazeDurationTicks;
        this.wild = wild;
        this.market = market;
        this.messages = messages;
    }

    public static Yap420Config load(JavaPlugin plugin) {
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();
        Set<Material> soils = parseMaterials(c.getStringList("growth.soils"), Material.FARMLAND);
        Wild wild = Wild.load(c.getConfigurationSection("wild"));
        Set<Material> growth = EnumSet.noneOf(Material.class);
        growth.addAll(soils);
        growth.addAll(wild.soils());
        Messages msg = new Messages(
                c.getString("messages.planted", "&aPlanted &f{strain}&a."),
                c.getString("messages.harvested", "&aHarvested wet &f{strain}&a bud."),
                c.getString("messages.not-mature", "&cNot mature yet (stage &f{stage}&c/&f{max}&c)."),
                c.getString("messages.wrong-soil", "&cPlant on farmland."),
                c.getString("messages.no-space", "&cThat block is occupied."),
                c.getString("messages.deposited", "&aDrying &f{count}&a wet bud(s)."),
                c.getString("messages.cured-ready", "&aCured buds ready — right-click to collect."),
                c.getString("messages.collected", "&aCollected &f{count}&a cured bud(s)."),
                c.getString("messages.rack-full", "&cDrying rack is full."),
                c.getString("messages.need-wet", "&cHold wet buds to deposit."),
                c.getString("messages.reloaded", "&aYaP420 reloaded."),
                c.getString("messages.removed", "&aRemoved YaP420 plot/rack."),
                c.getString("messages.nothing", "&cNothing YaP420 here.")
        );
        return new Yap420Config(
                c.getBoolean("enabled", true),
                Math.max(20L, c.getLong("growth.check-period-ticks", 100L)),
                clamp01(c.getDouble("growth.advance-chance", 0.35)),
                Math.max(2, Math.min(12, c.getInt("growth.stages", 6))),
                Math.max(0, c.getInt("growth.min-light", 9)),
                c.getBoolean("growth.require-water-nearby", true),
                Math.max(1, c.getInt("growth.water-radius", 4)),
                Collections.unmodifiableSet(soils),
                Collections.unmodifiableSet(growth),
                Math.max(0.0, c.getDouble("growth.herbalism-xp", 12.0)),
                Math.max(20L, c.getLong("cure.check-period-ticks", 80L)),
                Math.max(1000L, c.getLong("cure.millis-per-bud", 45_000L)),
                Math.max(1, c.getInt("cure.max-slots", 8)),
                c.getString("haze.channel", "yap:420"),
                clamp01(c.getDouble("haze.default-intensity", 0.65)),
                Math.max(20, c.getInt("haze.default-duration-ticks", 200)),
                wild,
                MarketSettings.load(c),
                msg
        );
    }

    static Set<Material> parseMaterials(List<String> names, Material fallback) {
        Set<Material> out = EnumSet.noneOf(Material.class);
        if (names == null || names.isEmpty()) {
            out.add(fallback);
            return out;
        }
        for (String name : names) {
            Material mat = Material.matchMaterial(name.trim().toUpperCase(Locale.ROOT));
            if (mat != null) {
                out.add(mat);
            }
        }
        if (out.isEmpty()) {
            out.add(fallback);
        }
        return out;
    }

    private static double clamp01(double v) {
        if (v < 0) {
            return 0;
        }
        if (v > 1) {
            return 1;
        }
        return v;
    }

    public boolean enabled() {
        return enabled;
    }

    public long growthPeriodTicks() {
        return growthPeriodTicks;
    }

    public double advanceChance() {
        return advanceChance;
    }

    public int stages() {
        return stages;
    }

    public int maxStageIndex() {
        return stages - 1;
    }

    public int minLight() {
        return minLight;
    }

    public boolean requireWater() {
        return requireWater;
    }

    public int waterRadius() {
        return waterRadius;
    }

    /** Soils allowed for player planting. */
    public Set<Material> soils() {
        return soils;
    }

    /** Farm + wild soils for growth checks. */
    public Set<Material> growthSoils() {
        return growthSoils;
    }

    public double herbalismXp() {
        return herbalismXp;
    }

    public long curePeriodTicks() {
        return curePeriodTicks;
    }

    public long cureMillisPerBud() {
        return cureMillisPerBud;
    }

    public int rackMaxSlots() {
        return rackMaxSlots;
    }

    public String hazeChannel() {
        return hazeChannel;
    }

    public double hazeIntensity() {
        return hazeIntensity;
    }

    public int hazeDurationTicks() {
        return hazeDurationTicks;
    }

    public Wild wild() {
        return wild;
    }

    public MarketSettings market() {
        return market;
    }

    public Messages messages() {
        return messages;
    }

    public List<String> soilNames() {
        List<String> out = new ArrayList<>(soils.size());
        for (Material m : soils) {
            out.add(m.name());
        }
        return out;
    }

    /** Sparse natural plants on newly generated overworld chunks. */
    public record Wild(
            boolean enabled,
            double chancePerChunk,
            int attemptsPerChunk,
            int maxPerChunk,
            int minLight,
            int initialStageMin,
            int initialStageMax,
            Set<Material> soils,
            List<String> worlds,
            Set<String> biomes,
            List<StrainId> strains
    ) {
        static Wild load(ConfigurationSection sec) {
            if (sec == null) {
                return defaults();
            }
            Set<Material> soils = parseMaterials(
                    sec.getStringList("soils"),
                    Material.GRASS_BLOCK);
            List<StrainId> strains = new ArrayList<>();
            for (String raw : sec.getStringList("strains")) {
                StrainId.parse(raw).ifPresent(strains::add);
            }
            if (strains.isEmpty()) {
                strains.add(StrainId.SATIVA);
                strains.add(StrainId.INDICA);
            }
            Set<String> biomes = new HashSet<>();
            for (String b : sec.getStringList("biomes")) {
                if (b != null && !b.isBlank()) {
                    biomes.add(b.trim().toLowerCase(Locale.ROOT));
                }
            }
            List<String> worlds = new ArrayList<>();
            for (String w : sec.getStringList("worlds")) {
                if (w != null && !w.isBlank()) {
                    worlds.add(w.trim());
                }
            }
            return new Wild(
                    sec.getBoolean("enabled", true),
                    clamp01(sec.getDouble("chance-per-chunk", 0.14)),
                    Math.max(1, sec.getInt("attempts-per-chunk", 4)),
                    Math.max(1, sec.getInt("max-per-chunk", 2)),
                    Math.max(0, sec.getInt("min-light", 8)),
                    Math.max(0, sec.getInt("initial-stage-min", 0)),
                    Math.max(0, sec.getInt("initial-stage-max", 3)),
                    Collections.unmodifiableSet(soils),
                    List.copyOf(worlds),
                    Collections.unmodifiableSet(biomes),
                    List.copyOf(strains)
            );
        }

        static Wild defaults() {
            return new Wild(
                    true,
                    0.14,
                    4,
                    2,
                    8,
                    0,
                    3,
                    Set.of(
                            Material.GRASS_BLOCK,
                            Material.DIRT,
                            Material.PODZOL,
                            Material.ROOTED_DIRT,
                            Material.MOSS_BLOCK),
                    List.of(),
                    Set.of(),
                    List.of(StrainId.SATIVA, StrainId.INDICA)
            );
        }
    }

    public record Messages(
            String planted,
            String harvested,
            String notMature,
            String wrongSoil,
            String noSpace,
            String deposited,
            String curedReady,
            String collected,
            String rackFull,
            String needWet,
            String reloaded,
            String removed,
            String nothing
    ) {
    }
}
