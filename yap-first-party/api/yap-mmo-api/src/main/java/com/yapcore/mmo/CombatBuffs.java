package com.yapcore.mmo;

/** Active potion-style combat boosts (attack / strength / defence). */
public record CombatBuffs(int attackBoost, int strengthBoost, int defenceBoost) {

    public static final CombatBuffs NONE = new CombatBuffs(0, 0, 0);
}
