package com.yapcore.abilities;

public record StatModifiers(
        int attackBoost,
        int strengthBoost,
        int defenceBoost,
        int rangedBoost,
        int magicBoost,
        double speedMultiplier,
        double damageTakenMultiplier) {

    public StatModifiers {
        speedMultiplier = speedMultiplier <= 0 ? 1.0 : speedMultiplier;
        damageTakenMultiplier = damageTakenMultiplier <= 0 ? 1.0 : damageTakenMultiplier;
    }

    public static StatModifiers empty() {
        return new StatModifiers(0, 0, 0, 0, 0, 1.0, 1.0);
    }
}
