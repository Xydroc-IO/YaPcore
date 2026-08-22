package com.yapcore.mmo;

/** Aggregated equipment bonuses from config and item metadata. */
public record GearBonus(
        int attackBonus,
        int strengthBonus,
        int defenceBonus,
        int prayerBonus,
        int rangedBonus,
        int magicBonus) {

    public static final GearBonus ZERO = new GearBonus(0, 0, 0, 0, 0, 0);

    public GearBonus add(GearBonus other) {
        return new GearBonus(
                attackBonus + other.attackBonus,
                strengthBonus + other.strengthBonus,
                defenceBonus + other.defenceBonus,
                prayerBonus + other.prayerBonus,
                rangedBonus + other.rangedBonus,
                magicBonus + other.magicBonus);
    }
}
