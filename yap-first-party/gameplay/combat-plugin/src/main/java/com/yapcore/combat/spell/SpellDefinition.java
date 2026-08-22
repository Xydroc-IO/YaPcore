package com.yapcore.combat.spell;

import org.bukkit.Material;

import java.util.Map;

public record SpellDefinition(
        String id,
        String displayName,
        int minMagicLevel,
        int prayerCost,
        int baseMaxHit,
        double castXp,
        double damageXpMultiplier,
        Map<Material, Integer> runes,
        Material requiredStaff,
        String targetFilter,
        String appliesEffect,
        int effectStacks) {

    public SpellDefinition {
        runes = runes == null ? Map.of() : Map.copyOf(runes);
        if (effectStacks < 1) {
            effectStacks = 1;
        }
    }

    public SpellDefinition(
            String id,
            String displayName,
            int minMagicLevel,
            int prayerCost,
            int baseMaxHit,
            double castXp,
            double damageXpMultiplier,
            Map<Material, Integer> runes,
            Material requiredStaff,
            String targetFilter) {
        this(id, displayName, minMagicLevel, prayerCost, baseMaxHit, castXp, damageXpMultiplier,
                runes, requiredStaff, targetFilter, null, 1);
    }

    public boolean requiresStaff() {
        return requiredStaff != null;
    }

    public boolean hasTargetFilter() {
        return targetFilter != null && !targetFilter.isBlank();
    }
}
