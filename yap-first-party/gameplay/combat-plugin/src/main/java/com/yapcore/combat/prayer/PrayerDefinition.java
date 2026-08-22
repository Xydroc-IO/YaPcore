package com.yapcore.combat.prayer;

public record PrayerDefinition(
        String id,
        String displayName,
        int minPrayerLevel,
        int drainPerTick,
        String group,
        PrayerEffectType effectType,
        int statBoost,
        double damageReduction) {
}
