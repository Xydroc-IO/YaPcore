package com.yapcore.mmo;

import org.bukkit.Material;

import java.util.Map;

public record SkillDefinition(
        SkillId id,
        String display,
        Material icon,
        int iconCmd,
        boolean enabled,
        Map<Material, BreakAction> breakActions,
        Map<String, FishAction> fishActions,
        Map<Material, SmeltAction> smeltActions,
        CombatDealtAction combatDealt,
        CombatDealtAction rangedDealt,
        CombatDealtAction magicDealt,
        CombatTakenAction combatTaken,
        HitpointsRatio hitpointsRatio,
        PrayerDrainAction prayerDrain
) {
    /** Back-compat without custom model data. */
    public SkillDefinition(
            SkillId id,
            String display,
            Material icon,
            boolean enabled,
            Map<Material, BreakAction> breakActions,
            Map<String, FishAction> fishActions,
            Map<Material, SmeltAction> smeltActions,
            CombatDealtAction combatDealt,
            CombatDealtAction rangedDealt,
            CombatDealtAction magicDealt,
            CombatTakenAction combatTaken,
            HitpointsRatio hitpointsRatio,
            PrayerDrainAction prayerDrain) {
        this(id, display, icon, 0, enabled, breakActions, fishActions, smeltActions,
                combatDealt, rangedDealt, magicDealt, combatTaken, hitpointsRatio, prayerDrain);
    }

    public record BreakAction(double xp, int minLevel) {
    }

    public record FishAction(double xp, int minLevel) {
    }

    public record SmeltAction(double xp, int minLevel) {
    }

    /** XP per damage dealt; {@code share} splits combined combat XP (e.g. 0.5 attack / 0.5 strength). */
    public record CombatDealtAction(double xpPerDamage, double share) {
    }

    /** XP per damage taken (defence). */
    public record CombatTakenAction(double xpPerDamage) {
    }

    /** Fraction of combined attack+strength XP awarded to hitpoints (RS-style ~1/3). */
    public record HitpointsRatio(double ratio) {
    }

    /** XP per prayer point drained while prayers are active. */
    public record PrayerDrainAction(double xpPerPoint) {
    }
}
