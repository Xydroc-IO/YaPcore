package com.yapcore.combat.status;

/** Aggregated combat modifiers from active status effects on an entity. */
public record StatusModifiers(
        int attackBoost,
        int strengthBoost,
        int defenceBoost,
        double outgoingDamageMultiplier,
        double incomingDamageMultiplier,
        double movementScale,
        boolean blocksAttacks) {

    public static StatusModifiers none() {
        return new StatusModifiers(0, 0, 0, 1.0, 1.0, 1.0, false);
    }

    public StatusModifiers merge(StatusModifiers other) {
        return new StatusModifiers(
                attackBoost + other.attackBoost,
                strengthBoost + other.strengthBoost,
                defenceBoost + other.defenceBoost,
                outgoingDamageMultiplier * other.outgoingDamageMultiplier,
                incomingDamageMultiplier * other.incomingDamageMultiplier,
                Math.min(movementScale, other.movementScale),
                blocksAttacks || other.blocksAttacks);
    }
}
