package com.yapcore.combat.prayer;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PrayerEffectResolverTest {

    @Test
    void stacksStatBoostsAndProtections() {
        Map<String, PrayerDefinition> prayers = Map.of(
                "thick_skin", new PrayerDefinition(
                        "thick_skin", "Thick Skin", 1, 1, "defence_boost",
                        PrayerEffectType.DEFENCE_BOOST, 5, 0),
                "hawk_eye", new PrayerDefinition(
                        "hawk_eye", "Hawk Eye", 26, 2, "ranged_boost",
                        PrayerEffectType.RANGED_BOOST, 8, 0),
                "protect_from_melee", new PrayerDefinition(
                        "protect_from_melee", "Protect from Melee", 43, 3, "overhead",
                        PrayerEffectType.PROTECT_MELEE, 0, 0.4));
        PrayerModifiers mods = PrayerEffectResolver.resolve(
                Set.of("thick_skin", "hawk_eye", "protect_from_melee"), prayers);
        assertEquals(5, mods.defenceBoost());
        assertEquals(8, mods.rangedBoost());
        assertEquals(0.4, mods.protectMelee(), 0.001);
    }
}
