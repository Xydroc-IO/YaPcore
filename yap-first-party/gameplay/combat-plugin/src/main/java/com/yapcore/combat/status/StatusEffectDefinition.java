package com.yapcore.combat.status;

public record StatusEffectDefinition(
        String id,
        String displayName,
        StatusEffectKind kind,
        int durationSeconds,
        int maxStacks,
        int tickIntervalSeconds,
        int damagePerTick,
        int healPerTick,
        int attackModifier,
        int strengthModifier,
        int defenceModifier,
        double damageTakenMultiplier,
        double movementScale,
        boolean blocksAttacks) {

    public StatusEffectDefinition {
        if (maxStacks < 1) {
            maxStacks = 1;
        }
        if (tickIntervalSeconds < 1) {
            tickIntervalSeconds = 1;
        }
        if (movementScale <= 0 || movementScale > 1) {
            movementScale = 1.0;
        }
        if (damageTakenMultiplier <= 0) {
            damageTakenMultiplier = 1.0;
        }
    }
}
