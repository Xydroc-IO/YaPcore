package com.yapcore.combat.prayer;

public record PrayerModifiers(
        int attackBoost,
        int strengthBoost,
        int defenceBoost,
        int rangedBoost,
        int magicBoost,
        double protectMelee,
        double protectMissiles,
        double protectMagic) {

    public static final PrayerModifiers NONE = new PrayerModifiers(0, 0, 0, 0, 0, 0, 0, 0);
}
