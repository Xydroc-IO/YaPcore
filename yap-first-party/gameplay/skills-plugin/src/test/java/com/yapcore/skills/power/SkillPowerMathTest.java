package com.yapcore.skills.power;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillPowerMathTest {

    @Test
    void levelOneIsVanilla() {
        assertEquals(1.0, SkillPowerMath.breakSpeed(1, 120, 1.5), 1.0e-9);
        assertEquals(1.0, SkillPowerMath.damageMultiplier(1, 120, 1.0), 1.0e-9);
        assertEquals(0.0, SkillPowerMath.expectedExtra(1, 120, 1.0), 1.0e-9);
        Random random = new Random(7);
        for (int i = 0; i < 40; i++) {
            assertEquals(0, SkillPowerMath.extraCopies(1, 120, 1.0, random));
        }
    }

    @Test
    void maxLevelReachesConfiguredCap() {
        assertEquals(2.5, SkillPowerMath.breakSpeed(120, 120, 1.5), 1.0e-9);
        assertEquals(2.0, SkillPowerMath.damageMultiplier(120, 120, 1.0), 1.0e-9);
        assertEquals(1, SkillPowerMath.extraCopies(120, 120, 1.0, always(0.0)));
        assertEquals(2, SkillPowerMath.extraCopies(120, 120, 2.0, always(0.99)));
    }

    @Test
    void midLevelIsBetweenVanillaAndCap() {
        double speed = SkillPowerMath.breakSpeed(60, 120, 1.5);
        assertTrue(speed > 1.0 && speed < 2.5);
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
        SkillPowerSettings settings = new SkillPowerSettings(true, -5, -2, -1);
        assertEquals(0.0, settings.breakSpeedBonusAtMax(), 0.0);
        assertEquals(0.0, settings.extraDropsAtMax(), 0.0);
        assertEquals(0.0, settings.damageBonusAtMax(), 0.0);
    }

    @Test
    void textMatchesTheLiveSkillsOnly() {
        SkillPowerSettings settings = SkillPowerSettings.defaults();
        assertEquals("Break speed: 1.00x", SkillPowerText.lines("mining", 1, 120, settings).get(0));
        assertEquals("Extra drops: none", SkillPowerText.lines("woodcutting", 1, 120, settings).get(1));
        assertEquals("Break speed: 2.50x", SkillPowerText.lines("Woodcutting", 120, 120, settings).get(0));
        assertEquals("Extra drops: +1", SkillPowerText.lines("mining", 120, 120, settings).get(1));
        assertEquals("Hit damage: 2.00x", SkillPowerText.lines("strength", 120, 120, settings).get(0));
        assertTrue(SkillPowerText.lines("attack", 120, 120, settings).isEmpty());
        assertTrue(SkillPowerText.lines("mining", 50, 120, new SkillPowerSettings(false, 1.5, 1.0, 1.0)).isEmpty());
        assertTrue(SkillPowerText.levelUpDetail("strength", 120, 120, settings).contains("Hit damage: 2.00x"));
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
