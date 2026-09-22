package com.yapcore.skills.power;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillPowerMathTest {

    @Test
    void levelOneIsVanilla() {
        assertEquals(1.0, SkillPowerMath.breakSpeed(1, 120, 1.5), 1.0e-9);
        assertEquals(1.0, SkillPowerMath.damageMultiplier(1, 120, 1.0), 1.0e-9);
        assertEquals(0.0, SkillPowerMath.expectedExtra(1, 120, 1.0), 1.0e-9);
        assertEquals(0.0, SkillPowerMath.placeReach(1, 120, 1.0), 1.0e-9);
        assertEquals(0.0, SkillPowerMath.keepBlockChance(1, 120, 0.25), 1.0e-9);
        assertEquals(0.0, SkillPowerMath.extraHearts(1, 120, 10.0), 1.0e-9);
        assertEquals(1.0, SkillPowerMath.brewSpeed(1, 120, 1.0), 1.0e-9);
        assertEquals(0.0, SkillPowerMath.swimWaterEfficiency(1, 120, 1.0), 1.0e-9);
        assertEquals(0.0, SkillPowerMath.swimOxygenBonus(1, 120, 8.0), 1.0e-9);
        Random random = new Random(7);
        for (int i = 0; i < 40; i++) {
            assertEquals(0, SkillPowerMath.extraCopies(1, 120, 1.0, random));
        }
    }

    @Test
    void maxLevelReachesConfiguredCap() {
        assertEquals(3.0, SkillPowerMath.breakSpeed(120, 120, 2.0), 1.0e-9);
        assertEquals(3.0, SkillPowerMath.damageMultiplier(120, 120, 2.0), 1.0e-9);
        assertEquals(2.0, SkillPowerMath.moveSpeed(120, 120, 1.0), 1.0e-9);
        assertEquals(1.0, SkillPowerMath.placeReach(120, 120, 1.0), 1.0e-9);
        assertEquals(0.25, SkillPowerMath.keepBlockChance(120, 120, 0.25), 1.0e-9);
        assertEquals(10.0, SkillPowerMath.extraHearts(120, 120, 10.0), 1.0e-9);
        assertEquals(2.0, SkillPowerMath.brewSpeed(120, 120, 1.0), 1.0e-9);
        assertEquals(1.0, SkillPowerMath.swimWaterEfficiency(120, 120, 1.0), 1.0e-9);
        assertEquals(8.0, SkillPowerMath.swimOxygenBonus(120, 120, 8.0), 1.0e-9);
        assertTrue(SkillPowerMath.atMax(120, 120));
        assertEquals(2, SkillPowerMath.extraCopies(120, 120, 2.0, always(0.0)));
        assertEquals(2, SkillPowerMath.extraCopies(120, 120, 2.0, always(0.99)));
    }

    @Test
    void midLevelIsBetweenVanillaAndCap() {
        double speed = SkillPowerMath.breakSpeed(60, 120, 2.0);
        assertTrue(speed > 1.0 && speed < 3.0);
        assertEquals(1, SkillPowerMath.extraCopies(60, 120, 1.0, always(0.0)));
        assertEquals(0, SkillPowerMath.extraCopies(60, 120, 1.0, always(0.99)));
    }

    @Test
    void levelsOutsideTheTableClamp() {
        assertEquals(SkillPowerMath.breakSpeed(1, 120, 1.5), SkillPowerMath.breakSpeed(0, 120, 1.5), 1.0e-9);
        assertEquals(SkillPowerMath.breakSpeed(120, 120, 1.5), SkillPowerMath.breakSpeed(500, 120, 1.5), 1.0e-9);
        assertEquals(1.0, SkillPowerMath.damageMultiplier(80, 120, 0.0), 1.0e-9);
    }

    @Test
    void negativeSettingsClampToZeroBonus() {
        SkillPowerSettings settings = new SkillPowerSettings(true, -5, -2, -1, -3, -4, -2, -8, -1, -1, -1, SkillAbilitySettings.defaults());
        assertEquals(0.0, settings.breakSpeedBonusAtMax(), 0.0);
        assertEquals(0.0, settings.extraDropsAtMax(), 0.0);
        assertEquals(0.0, settings.damageBonusAtMax(), 0.0);
        assertEquals(0.0, settings.movementSpeedBonusAtMax(), 0.0);
        assertEquals(0.0, settings.placeReachBonusAtMax(), 0.0);
        assertEquals(0.0, settings.keepBlockChanceAtMax(), 0.0);
        assertEquals(0.0, settings.extraHeartsAtMax(), 0.0);
        assertEquals(0.0, settings.brewSpeedBonusAtMax(), 0.0);
    }

    @Test
    void textMatchesTheLiveSkillsOnly() {
        SkillPowerSettings settings = SkillPowerSettings.defaults();
        assertEquals("Break speed: 1.00x", SkillPowerText.lines("mining", 1, 120, settings).get(0));
        assertEquals("Extra drops: none", SkillPowerText.lines("woodcutting", 1, 120, settings).get(1));
        assertEquals("Break speed: 3.00x", SkillPowerText.lines("Woodcutting", 120, 120, settings).get(0));
        assertEquals("Extra drops: +2", SkillPowerText.lines("mining", 120, 120, settings).get(1));
        assertEquals("Hit damage: 3.00x", SkillPowerText.lines("strength", 120, 120, settings).get(0));
        assertEquals("Walk speed: 1.00x", SkillPowerText.lines("marathon", 1, 120, settings).get(0));
        assertEquals("Walk speed: 2.00x", SkillPowerText.lines("marathon", 120, 120, settings).get(0));
        assertEquals("Place reach: +0.00", SkillPowerText.lines("builder", 1, 120, settings).get(0));
        assertEquals("Keep block: none", SkillPowerText.lines("builder", 1, 120, settings).get(1));
        assertEquals("Place reach: +1.00", SkillPowerText.lines("builder", 120, 120, settings).get(0));
        assertEquals("Keep block: 25%", SkillPowerText.lines("builder", 120, 120, settings).get(1));
        assertEquals("Super Breaker: unlocks at max", SkillPowerText.lines("mining", 1, 120, settings).get(2));
        assertEquals("Super Breaker: sneak + right-click air", SkillPowerText.lines("mining", 120, 120, settings).get(2));
        assertEquals("Tree Feller: sneak + right-click air", SkillPowerText.lines("woodcutting", 120, 120, settings).get(2));
        assertEquals("Green Terra: harvest + replant", SkillPowerText.lines("herbalism", 120, 120, settings).get(0));
        assertEquals("Rare loot: clay, glowstone, diamonds", SkillPowerText.lines("excavation", 120, 120, settings).get(0));
        assertEquals("Brew speed: 2.00x", SkillPowerText.lines("alchemy", 120, 120, settings).get(0));
        assertEquals("Max health: +5.0 hearts", SkillPowerText.lines("health", 120, 120, settings).get(0));
        assertEquals("Regen: in combat", SkillPowerText.lines("health", 120, 120, settings).get(1));
        assertTrue(SkillPowerText.lines("attack", 120, 120, settings).isEmpty());
        assertTrue(SkillPowerText.lines("mining", 50, 120, new SkillPowerSettings(false, 2.0, 2.0, 2.0, 1.0, 1.0, 0.25, 10.0, 1.0, 1.0, 8.0, SkillAbilitySettings.defaults())).isEmpty());
        assertTrue(SkillPowerText.levelUpDetail("strength", 120, 120, settings).contains("Hit damage: 3.00x"));
        assertEquals("", SkillPowerText.levelUpDetail("excavation", 2, 120, settings));
        assertTrue(SkillPowerText.levelUpDetail("excavation", 120, 120, settings).contains("Rare loot"));
        assertFalse(SkillPowerText.levelUpDetail("mining", 1, 120, settings).contains("unlocks at max"));
        assertTrue(SkillPowerText.levelUpDetail("mining", 120, 120, settings).contains("Super Breaker"));
    }

    private static Random always(double value) {
        return new Random() {
            @Override
            public double nextDouble() {
                return value;
            }
        };
    }
}
