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
        PrayerDrainAction prayerDrain,
        TravelAction travel,
        PlaceAction place,
        BrewAction brew,
        java.util.List<TreasureDrop> treasure
) {
    public SkillDefinition {
        treasure = treasure == null ? java.util.List.of() : java.util.List.copyOf(treasure);
    }
    /** Back-compat without travel / place / custom model data. */
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
                combatDealt, rangedDealt, magicDealt, combatTaken, hitpointsRatio, prayerDrain, null, null, null, java.util.List.of());
    }

    /** Back-compat without travel / place. */
    public SkillDefinition(
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
            PrayerDrainAction prayerDrain) {
        this(id, display, icon, iconCmd, enabled, breakActions, fishActions, smeltActions,
                combatDealt, rangedDealt, magicDealt, combatTaken, hitpointsRatio, prayerDrain, null, null, null, java.util.List.of());
    }

    /** Back-compat without place. */
    public SkillDefinition(
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
            PrayerDrainAction prayerDrain,
            TravelAction travel) {
        this(id, display, icon, iconCmd, enabled, breakActions, fishActions, smeltActions,
                combatDealt, rangedDealt, magicDealt, combatTaken, hitpointsRatio, prayerDrain, travel, null, null, java.util.List.of());
    }

    /** Back-compat without brew / treasure. */
    public SkillDefinition(
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
            PrayerDrainAction prayerDrain,
            TravelAction travel,
            PlaceAction place) {
        this(id, display, icon, iconCmd, enabled, breakActions, fishActions, smeltActions,
                combatDealt, rangedDealt, magicDealt, combatTaken, hitpointsRatio, prayerDrain, travel, place,
                null, java.util.List.of());
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

    /** On-foot travel (walk/sprint/swim/climb). Blocks/sec cap stops ice-boat / fly farms. */
    public record TravelAction(double xpPerBlock, double maxBlocksPerSecond) {
    }

    /** XP for placing a block (builder). */
    public record PlaceAction(double xp) {
    }

    /** XP when a brewing stand finishes (alchemy). */
    public record BrewAction(double xp) {
    }

    /** Extra loot at max excavation. {@code chance} is 0–1 per block. */
    public record TreasureDrop(Material item, double chance, int amount) {
    }
}
