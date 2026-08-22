package com.yapcore.mmo;

/** OSRS-style combat level from combat skill levels. */
public final class CombatLevelCalculator {

    private CombatLevelCalculator() {
    }

    public static int calculate(
            int attack,
            int strength,
            int defence,
            int hitpoints,
            int prayer,
            int ranged,
            int magic) {
        double base = 0.25 * (defence + hitpoints + Math.floor(prayer / 2.0));
        double melee = 0.325 * (attack + strength);
        double rangedContrib = 0.325 * (ranged + Math.floor(ranged / 2.0));
        double magicContrib = 0.325 * (magic + Math.floor(magic / 2.0));
        return (int) Math.floor(base + Math.max(melee, Math.max(rangedContrib, magicContrib)));
    }
}
