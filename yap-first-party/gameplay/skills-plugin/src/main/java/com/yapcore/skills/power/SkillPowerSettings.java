package com.yapcore.skills.power;

import org.bukkit.configuration.file.FileConfiguration;

/** Tunables for skill power. Missing config keys use these defaults, and the feature is on. */
public record SkillPowerSettings(
        boolean enabled,
        double breakSpeedBonusAtMax,
        double extraDropsAtMax,
        double damageBonusAtMax) {

    public SkillPowerSettings {
        breakSpeedBonusAtMax = Math.max(0.0, breakSpeedBonusAtMax);
        extraDropsAtMax = Math.max(0.0, extraDropsAtMax);
        damageBonusAtMax = Math.max(0.0, damageBonusAtMax);
    }

    /** Level 120: 2.5x break speed, one extra drop copy, 2x hit damage. */
    public static SkillPowerSettings defaults() {
        return new SkillPowerSettings(true, 1.5, 1.0, 1.0);
    }

    public static SkillPowerSettings from(FileConfiguration config) {
        if (config == null) {
            return defaults();
        }
        return new SkillPowerSettings(
                config.getBoolean("power.enabled", true),
                config.getDouble("power.break-speed-bonus-at-max", 1.5),
                config.getDouble("power.extra-drops-at-max", 1.0),
                config.getDouble("power.damage-bonus-at-max", 1.0));
    }
}
