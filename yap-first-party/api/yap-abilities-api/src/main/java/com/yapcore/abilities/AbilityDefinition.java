package com.yapcore.abilities;

import java.util.List;
import java.util.Map;

public record AbilityDefinition(
        String id,
        String displayName,
        AbilityCategory category,
        Map<String, Integer> minLevels,
        AbilityCosts costs,
        int cooldownTicks,
        double range,
        TargetMode targetMode,
        String targetFilter,
        List<CastCondition> conditions,
        List<AbilityEffect> castEffects,
        List<AbilityEffect> hitEffects,
        ProjectileSpec projectile,
        int iconCmd) {

    public AbilityDefinition {
        minLevels = minLevels == null ? Map.of() : Map.copyOf(minLevels);
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        castEffects = castEffects == null ? List.of() : List.copyOf(castEffects);
        hitEffects = hitEffects == null ? List.of() : List.copyOf(hitEffects);
        cooldownTicks = Math.max(0, cooldownTicks);
        range = range <= 0 ? 20 : range;
    }

    /** Back-compat without conditions/icon. */
    public AbilityDefinition(
            String id,
            String displayName,
            AbilityCategory category,
            Map<String, Integer> minLevels,
            AbilityCosts costs,
            int cooldownTicks,
            double range,
            TargetMode targetMode,
            String targetFilter,
            List<AbilityEffect> castEffects,
            List<AbilityEffect> hitEffects,
            ProjectileSpec projectile) {
        this(id, displayName, category, minLevels, costs, cooldownTicks, range, targetMode,
                targetFilter, List.of(), castEffects, hitEffects, projectile, 0);
    }

    public int minLevel(String skillId) {
        return minLevels.getOrDefault(skillId, 1);
    }

    public boolean hasProjectile() {
        return projectile != null;
    }

    public boolean hasTargetFilter() {
        return targetFilter != null && !targetFilter.isBlank();
    }

    public int resolvedIconCmd() {
        if (iconCmd > 0) {
            return iconCmd;
        }
        if (projectile != null && projectile.iconCmd() > 0) {
            return projectile.iconCmd();
        }
        return 0;
    }
}
