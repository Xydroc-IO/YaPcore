package com.yapcore.combat.formula;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DamageCalculatorTest {

    private static final DamageCalculator.Params PARAMS = new DamageCalculator.Params(0.5, 1);

    @Test
    void levelOneUnarmedStillHasPositiveMaxHit() {
        var fists = new DamageCalculator.Attacker(1, 1, 0, 0, 0, 0);
        assertTrue(DamageCalculator.maxHit(fists, PARAMS) >= 1);
        var defender = new DamageCalculator.Defender(1, 0, 0);
        Random alwaysHigh = new Random(1L) {
            @Override
            public int nextInt(int bound) {
                return bound > 0 ? bound - 1 : 0;
            }
        };
        DamageCalculator.Result result = DamageCalculator.roll(fists, defender, PARAMS, alwaysHigh);
        assertTrue(result.hit());
        assertTrue(result.damage() >= 1);
    }

    @Test
    void maxHitScalesWithStrengthAndGear() {
        var low = new DamageCalculator.Attacker(1, 10, 0, 0, 0, 0);
        var high = new DamageCalculator.Attacker(1, 50, 0, 20, 0, 5);
        assertEquals(5, DamageCalculator.maxHit(low, PARAMS));
        assertEquals(37, DamageCalculator.maxHit(high, PARAMS));
    }

    @Test
    void rangedMaxHitUsesRangedLevelAndBonus() {
        var low = new DamageCalculator.RangedAttacker(10, 0, 0);
        var high = new DamageCalculator.RangedAttacker(50, 20, 5);
        assertEquals(5, DamageCalculator.rangedMaxHit(low, PARAMS));
        assertEquals(37, DamageCalculator.rangedMaxHit(high, PARAMS));
    }

    @Test
    void magicMaxHitScalesWithLevel() {
        var caster = new DamageCalculator.MagicAttacker(50, 10, 0);
        int hit = DamageCalculator.magicMaxHit(caster, 8, PARAMS);
        assertTrue(hit >= 8);
    }

    @Test
    void highDefenceBlocksWeakAttacks() {
        var attacker = new DamageCalculator.Attacker(1, 10, 0, 0, 0, 0);
        var defender = new DamageCalculator.Defender(99, 50, 10);
        Random alwaysLow = new Random(42L) {
            @Override
            public int nextInt(int bound) {
                return 0;
            }
        };
        DamageCalculator.Result result = DamageCalculator.roll(attacker, defender, PARAMS, alwaysLow);
        assertFalse(result.hit());
        assertEquals(0, result.damage());
    }

    @Test
    void rangedRollUsesRangedAccuracy() {
        var attacker = new DamageCalculator.RangedAttacker(60, 15, 0);
        var defender = new DamageCalculator.Defender(1, 0, 0);
        Random alwaysHigh = new Random(1L) {
            @Override
            public int nextInt(int bound) {
                return bound > 0 ? bound - 1 : 0;
            }
        };
        DamageCalculator.Result result = DamageCalculator.rollRanged(attacker, defender, PARAMS, alwaysHigh);
        assertTrue(result.hit());
        assertTrue(result.damage() >= 1);
    }

    @Test
    void successfulHitRespectsMinDamage() {
        var attacker = new DamageCalculator.Attacker(99, 80, 30, 20, 10, 0);
        var defender = new DamageCalculator.Defender(1, 0, 0);
        Random alwaysLow = new Random(7L) {
            @Override
            public int nextInt(int bound) {
                return 0;
            }
        };
        DamageCalculator.Result result = DamageCalculator.roll(attacker, defender, PARAMS, alwaysLow);
        assertTrue(result.hit());
        assertTrue(result.damage() >= 1);
        assertTrue(result.damage() <= result.maxHit());
    }

    @Test
    void potionBoostsIncreaseMaxHit() {
        var base = new DamageCalculator.Attacker(40, 40, 10, 10, 0, 0);
        var boosted = new DamageCalculator.Attacker(40, 40, 10, 10, 0, 8);
        assertTrue(DamageCalculator.maxHit(boosted, PARAMS) > DamageCalculator.maxHit(base, PARAMS));
    }

    @Test
    void critChanceCanTriggerOnHit() {
        var attacker = new DamageCalculator.Attacker(99, 80, 30, 20, 10, 0);
        var defender = new DamageCalculator.Defender(1, 0, 0);
        var params = new DamageCalculator.Params(0.5, 1, 1.0, 2.0);
        java.util.Random alwaysCrit = new java.util.Random(0L) {
            @Override
            public int nextInt(int bound) {
                return bound > 0 ? bound - 1 : 0;
            }

            @Override
            public double nextDouble() {
                return 0.0;
            }
        };
        DamageCalculator.Result result = DamageCalculator.roll(attacker, defender, params, alwaysCrit);
        assertTrue(result.hit());
        assertTrue(result.critical());
        assertTrue(result.damage() >= 1);
    }
}
