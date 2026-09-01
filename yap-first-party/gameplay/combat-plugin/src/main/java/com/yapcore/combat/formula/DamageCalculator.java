package com.yapcore.combat.formula;

/**
 * Configurable RuneScape-lite hit roll and max-hit calculation.
 * Pure logic — unit tested without Bukkit.
 */
public final class DamageCalculator {

    public record Params(
            double levelFactor,
            int minDamageOnHit,
            double critChance,
            double critMultiplier) {
        public Params(double levelFactor, int minDamageOnHit) {
            this(levelFactor, minDamageOnHit, 0.0, 1.5);
        }

        public Params {
            if (levelFactor <= 0) {
                levelFactor = 0.5;
            }
            if (minDamageOnHit < 0) {
                minDamageOnHit = 0;
            }
            if (critChance < 0) {
                critChance = 0;
            }
            if (critMultiplier < 1.0) {
                critMultiplier = 1.0;
            }
        }
    }

    public record Attacker(
            int attackLevel,
            int strengthLevel,
            int attackBonus,
            int strengthBonus,
            int attackBoost,
            int strengthBoost) {
    }

    public record RangedAttacker(
            int rangedLevel,
            int rangedBonus,
            int rangedBoost) {
    }

    public record MagicAttacker(int magicLevel, int magicBonus, int magicBoost) {
    }

    public record Defender(int defenceLevel, int defenceBonus, int defenceBoost) {
    }

    public record Result(boolean hit, int damage, int maxHit, boolean critical) {
        public Result(boolean hit, int damage, int maxHit) {
            this(hit, damage, maxHit, false);
        }
    }

    private DamageCalculator() {
    }

    public static int maxHit(Attacker attacker, Params params) {
        int effectiveStrength = attacker.strengthLevel() + attacker.strengthBonus() + attacker.strengthBoost();
        if (effectiveStrength < 0) {
            effectiveStrength = 0;
        }
        int raw = (int) Math.floor(effectiveStrength * params.levelFactor());
        // Level-1 unarmed used to floor to 0 → cancel vanilla + deal no damage (mobs look immortal).
        int floor = Math.max(1, params.minDamageOnHit());
        return Math.max(floor, raw);
    }

    public static int rangedMaxHit(RangedAttacker attacker, Params params) {
        int effective = attacker.rangedLevel() + attacker.rangedBonus() + attacker.rangedBoost();
        if (effective < 0) {
            effective = 0;
        }
        int raw = (int) Math.floor(effective * params.levelFactor());
        int floor = Math.max(1, params.minDamageOnHit());
        return Math.max(floor, raw);
    }

    public static int magicMaxHit(MagicAttacker attacker, int spellBaseMaxHit, Params params) {
        if (spellBaseMaxHit <= 0) {
            return 0;
        }
        int effective = attacker.magicLevel() + attacker.magicBonus() + attacker.magicBoost();
        double scale = 1.0 + Math.max(0, effective - 1) * params.levelFactor() * 0.02;
        return Math.max(1, (int) Math.floor(spellBaseMaxHit * scale));
    }

    public static Result roll(Attacker attacker, Defender defender, Params params, java.util.Random random) {
        int maxHit = maxHit(attacker, params);
        return rollAccuracy(
                attacker.attackLevel() + attacker.attackBonus() + attacker.attackBoost(),
                maxHit,
                defender,
                params,
                random);
    }

    public static Result rollRanged(
            RangedAttacker attacker,
            Defender defender,
            Params params,
            java.util.Random random) {
        int maxHit = rangedMaxHit(attacker, params);
        return rollAccuracy(
                attacker.rangedLevel() + attacker.rangedBonus() + attacker.rangedBoost(),
                maxHit,
                defender,
                params,
                random);
    }

    public static Result rollMagic(
            MagicAttacker attacker,
            Defender defender,
            int spellBaseMaxHit,
            Params params,
            java.util.Random random) {
        int maxHit = magicMaxHit(attacker, spellBaseMaxHit, params);
        return rollAccuracy(
                attacker.magicLevel() + attacker.magicBonus() + attacker.magicBoost(),
                maxHit,
                defender,
                params,
                random);
    }

    private static Result rollAccuracy(
            int effectiveAttack,
            int maxHit,
            Defender defender,
            Params params,
            java.util.Random random) {
        int effectiveDefence = defender.defenceLevel() + defender.defenceBonus() + defender.defenceBoost();
        if (effectiveAttack < 0) {
            effectiveAttack = 0;
        }
        if (effectiveDefence < 0) {
            effectiveDefence = 0;
        }

        int attackRoll = effectiveAttack + rollInclusive(random, effectiveAttack);
        int defenceRoll = effectiveDefence + rollInclusive(random, effectiveDefence);
        // Ties count as hits so level-1 vs weak mobs (chicken def=1) are not soft-locked.
        if (attackRoll < defenceRoll) {
            return new Result(false, 0, Math.max(maxHit, Math.max(1, params.minDamageOnHit())));
        }

        if (maxHit <= 0) {
            maxHit = Math.max(1, params.minDamageOnHit());
        }

        int damage = random.nextInt(maxHit + 1);
        if (damage < params.minDamageOnHit()) {
            damage = params.minDamageOnHit();
        }
        if (damage < 1) {
            damage = 1;
        }
        if (damage > maxHit) {
            damage = maxHit;
        }
        boolean critical = params.critChance() > 0 && random.nextDouble() < params.critChance();
        if (critical) {
            damage = (int) Math.ceil(damage * params.critMultiplier());
            if (damage > maxHit) {
                damage = maxHit;
            }
        }
        return new Result(true, damage, maxHit, critical);
    }

    private static int rollInclusive(java.util.Random random, int maxInclusive) {
        if (maxInclusive <= 0) {
            return 0;
        }
        return random.nextInt(maxInclusive + 1);
    }
}
