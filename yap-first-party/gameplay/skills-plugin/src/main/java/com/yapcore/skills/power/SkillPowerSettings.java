package com.yapcore.skills.power;

import org.bukkit.configuration.file.FileConfiguration;

/** Tunables for skill power. Missing config keys use these defaults, and the feature is on. */
public record SkillPowerSettings(
        boolean enabled,
        double breakSpeedBonusAtMax,
        double extraDropsAtMax,
        double damageBonusAtMax,
        double movementSpeedBonusAtMax,
        double placeReachBonusAtMax,
        double keepBlockChanceAtMax,
        double extraHeartsAtMax,
        double brewSpeedBonusAtMax,
        double swimWaterEfficiencyAtMax,
        double swimOxygenBonusAtMax,
        SkillAbilitySettings abilities) {

    public SkillPowerSettings {
        breakSpeedBonusAtMax = Math.max(0.0, breakSpeedBonusAtMax);
        extraDropsAtMax = Math.max(0.0, extraDropsAtMax);
        damageBonusAtMax = Math.max(0.0, damageBonusAtMax);
        movementSpeedBonusAtMax = Math.max(0.0, movementSpeedBonusAtMax);
        placeReachBonusAtMax = Math.max(0.0, placeReachBonusAtMax);
        keepBlockChanceAtMax = Math.max(0.0, Math.min(1.0, keepBlockChanceAtMax));
        extraHeartsAtMax = Math.max(0.0, extraHeartsAtMax);
        brewSpeedBonusAtMax = Math.max(0.0, brewSpeedBonusAtMax);
        swimWaterEfficiencyAtMax = Math.max(0.0, swimWaterEfficiencyAtMax);
        swimOxygenBonusAtMax = Math.max(0.0, swimOxygenBonusAtMax);
        abilities = abilities == null ? SkillAbilitySettings.defaults() : abilities;
    }

    /** Level 120: 3x mine/chop, 3x hits, 1.5x walk, +5 hearts, 2x brew, swim Depth-Strider-like + air. */
    public static SkillPowerSettings defaults() {
        return new SkillPowerSettings(true, 2.0, 2.0, 2.0, 0.5, 1.0, 0.25, 10.0, 1.0,
                1.0, 8.0, SkillAbilitySettings.defaults());
    }

    public static SkillPowerSettings from(FileConfiguration config) {
        if (config == null) {
            return defaults();
        }
        return new SkillPowerSettings(
                config.getBoolean("power.enabled", true),
                config.getDouble("power.break-speed-bonus-at-max", 2.0),
                config.getDouble("power.extra-drops-at-max", 2.0),
                config.getDouble("power.damage-bonus-at-max", 2.0),
                config.getDouble("power.movement-speed-bonus-at-max", 0.5),
                config.getDouble("power.place-reach-bonus-at-max", 1.0),
                config.getDouble("power.keep-block-chance-at-max", 0.25),
                config.getDouble("power.extra-hearts-at-max", 10.0),
                config.getDouble("power.brew-speed-bonus-at-max", 1.0),
                config.getDouble("power.swim-water-efficiency-at-max", 1.0),
                config.getDouble("power.swim-oxygen-bonus-at-max", 8.0),
                SkillAbilitySettings.from(config));
    }
}
