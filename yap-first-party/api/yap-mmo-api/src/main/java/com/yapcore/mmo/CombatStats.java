package com.yapcore.mmo;

/** Resolved combat skill levels, gear bonuses, and current prayer/HP pools. */
public record CombatStats(
        int attack,
        int strength,
        int defence,
        int hitpoints,
        int prayer,
        int ranged,
        int magic,
        GearBonus gear,
        CombatBuffs buffs,
        int currentHp,
        int maxHp,
        int currentPrayer,
        int maxPrayer) {
}
