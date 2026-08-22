package com.yapcore.combat.prayer;

import java.util.Map;
import java.util.Set;

public final class PrayerEffectResolver {

    private PrayerEffectResolver() {
    }

    public static PrayerModifiers resolve(Set<String> activePrayers, Map<String, PrayerDefinition> prayers) {
        if (activePrayers == null || activePrayers.isEmpty()) {
            return PrayerModifiers.NONE;
        }
        int attack = 0;
        int strength = 0;
        int defence = 0;
        int ranged = 0;
        int magic = 0;
        double protectMelee = 0;
        double protectMissiles = 0;
        double protectMagic = 0;
        for (String id : activePrayers) {
            PrayerDefinition def = prayers.get(id);
            if (def == null) {
                continue;
            }
            switch (def.effectType()) {
                case ATTACK_BOOST -> attack += def.statBoost();
                case STRENGTH_BOOST -> strength += def.statBoost();
                case DEFENCE_BOOST -> defence += def.statBoost();
                case RANGED_BOOST -> ranged += def.statBoost();
                case MAGIC_BOOST -> magic += def.statBoost();
                case PROTECT_MELEE -> protectMelee = Math.max(protectMelee, def.damageReduction());
                case PROTECT_MISSILES -> protectMissiles = Math.max(protectMissiles, def.damageReduction());
                case PROTECT_MAGIC -> protectMagic = Math.max(protectMagic, def.damageReduction());
                default -> {
                }
            }
        }
        return new PrayerModifiers(
                attack, strength, defence, ranged, magic,
                protectMelee, protectMissiles, protectMagic);
    }
}
