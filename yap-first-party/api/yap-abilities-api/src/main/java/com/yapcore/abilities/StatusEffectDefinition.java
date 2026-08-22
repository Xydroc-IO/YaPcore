package com.yapcore.abilities;

import java.util.List;

public record StatusEffectDefinition(
        String id,
        String displayName,
        StatusKind kind,
        int maxStacks,
        int durationTicks,
        String group,
        StatModifiers modifiers,
        List<AbilityEffect> tickEffects,
        int tickInterval,
        List<AbilityEffect> expireEffects) {

    public StatusEffectDefinition {
        maxStacks = Math.max(1, maxStacks);
        durationTicks = Math.max(1, durationTicks);
        group = group == null ? "" : group;
        modifiers = modifiers == null ? StatModifiers.empty() : modifiers;
        tickEffects = tickEffects == null ? List.of() : List.copyOf(tickEffects);
        tickInterval = Math.max(1, tickInterval);
        expireEffects = expireEffects == null ? List.of() : List.copyOf(expireEffects);
    }
}
