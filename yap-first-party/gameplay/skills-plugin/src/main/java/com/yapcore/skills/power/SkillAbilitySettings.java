package com.yapcore.skills.power;

import org.bukkit.configuration.file.FileConfiguration;

/** Max-level activated abilities and health regen. Missing keys use these defaults. */
public record SkillAbilitySettings(
        int durationTicks,
        int cooldownTicks,
        double superBreakerSpeed,
        int treeFellerMaxLogs,
        boolean greenTerra,
        boolean excavationTreasure,
        int healthRegenIntervalTicks,
        double healthRegenHp) {

    public SkillAbilitySettings {
        durationTicks = Math.max(20, durationTicks);
        cooldownTicks = Math.max(20, cooldownTicks);
        superBreakerSpeed = Math.max(1.0, superBreakerSpeed);
        treeFellerMaxLogs = Math.max(4, Math.min(64, treeFellerMaxLogs));
        healthRegenIntervalTicks = Math.max(20, healthRegenIntervalTicks);
        healthRegenHp = Math.max(0.0, healthRegenHp);
    }

    public static SkillAbilitySettings defaults() {
        return new SkillAbilitySettings(12 * 20, 90 * 20, 8.0, 32, true, true, 80, 1.0);
    }

    public static SkillAbilitySettings from(FileConfiguration config) {
        if (config == null) {
            return defaults();
        }
        return new SkillAbilitySettings(
                secondsToTicks(config.getInt("power.abilities.duration-seconds", 12)),
                secondsToTicks(config.getInt("power.abilities.cooldown-seconds", 90)),
                config.getDouble("power.abilities.super-breaker-speed", 8.0),
                config.getInt("power.abilities.tree-feller-max-logs", 32),
                config.getBoolean("power.abilities.green-terra", true),
                config.getBoolean("power.abilities.excavation-treasure", true),
                config.getInt("power.abilities.health-regen-interval-ticks", 80),
                config.getDouble("power.abilities.health-regen-hp", 1.0));
    }

    private static int secondsToTicks(int seconds) {
        return Math.max(1, seconds) * 20;
    }
}
