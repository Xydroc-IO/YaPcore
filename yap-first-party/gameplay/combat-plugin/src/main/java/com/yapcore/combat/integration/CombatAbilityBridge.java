package com.yapcore.combat.integration;

import com.yapcore.abilities.AbilityCombatBridge;
import com.yapcore.combat.formula.CombatHitResolver;
import com.yapcore.combat.formula.CombatPhysics;
import com.yapcore.combat.formula.DamageCalculator;
import com.yapcore.combat.integration.CombatPvpGate;
import com.yapcore.combat.prayer.PrayerModifiers;
import com.yapcore.combat.service.CombatServiceImpl;
import com.yapcore.combat.service.CombatXpAwarder;
import com.yapcore.mmo.CombatStats;
import com.yapcore.mmo.CombatStyle;
import com.yapcore.mmo.GearBonus;
import com.yapcore.sched.YapSched;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class CombatAbilityBridge implements AbilityCombatBridge {

    private final JavaPlugin plugin;
    private final CombatServiceImpl combat;
    private final CombatXpAwarder xp;

    public CombatAbilityBridge(JavaPlugin plugin, CombatServiceImpl combat, CombatXpAwarder xp) {
        this.plugin = plugin;
        this.combat = combat;
        this.xp = xp;
    }

    @Override
    public boolean applyDamage(Player attacker, LivingEntity target, String style, int maxHit, double xpMultiplier) {
        CombatStyle combatStyle = parseStyle(style);
        PrayerModifiers prayers = combat.prayerModifiers(attacker);
        GearBonus gear = combat.stats(attacker).gear();
        DamageCalculator.Params params = combat.formulaParams();

        return switch (combatStyle) {
            case MAGIC -> applyMagic(attacker, target, maxHit, xpMultiplier, prayers, gear, params);
            case RANGED -> applyRanged(attacker, target, maxHit, xpMultiplier, prayers, gear, params);
            case MELEE -> applyMelee(attacker, target, maxHit, xpMultiplier, prayers, gear, params);
        };
    }

    @Override
    public boolean isPvpAllowed(Player attacker, Player victim) {
        return CombatPvpGate.isPlayerVsPlayerAllowed(combat.config(), attacker, victim);
    }

    @Override
    public int currentPrayer(Player player) {
        return combat.stats(player).currentPrayer();
    }

    @Override
    public boolean drainPrayer(Player player, int amount) {
        if (combat.stats(player).currentPrayer() < amount) {
            return false;
        }
        combat.drainPrayer(player, amount);
        return true;
    }

    private boolean applyMagic(
            Player attacker,
            LivingEntity target,
            int maxHit,
            double xpMultiplier,
            PrayerModifiers prayers,
            GearBonus gear,
            DamageCalculator.Params params) {
        var magicAttacker = new DamageCalculator.MagicAttacker(
                combat.stats(attacker).magic(), gear.magicBonus(), prayers.magicBoost());
        var defender = defenderFor(target);
        YapSched.entity(plugin, attacker, () -> CombatHitResolver.resolveAsync(
                plugin,
                random -> DamageCalculator.rollMagic(magicAttacker, defender, maxHit, params, random),
                result -> YapSched.entity(plugin, attacker, () -> applyResult(attacker, target, result, CombatStyle.MAGIC,
                        () -> xp.awardMagicDamage(attacker.getUniqueId(), result.damage(), xpMultiplier)))));
        return true;
    }

    private boolean applyRanged(
            Player attacker,
            LivingEntity target,
            int maxHit,
            double xpMultiplier,
            PrayerModifiers prayers,
            GearBonus gear,
            DamageCalculator.Params params) {
        CombatStats stats = combat.stats(attacker);
        var rangedAttacker = new DamageCalculator.RangedAttacker(
                stats.ranged(), gear.rangedBonus(), prayers.rangedBoost());
        var defender = defenderFor(target);
        YapSched.entity(plugin, attacker, () -> CombatHitResolver.resolveAsync(
                plugin,
                random -> DamageCalculator.rollRanged(rangedAttacker, defender, params, random),
                result -> YapSched.entity(plugin, attacker, () -> applyResult(attacker, target, result, CombatStyle.RANGED,
                        () -> xp.awardDamageDealt(attacker.getUniqueId(), result.damage(), CombatStyle.RANGED)))));
        return true;
    }

    private boolean applyMelee(
            Player attacker,
            LivingEntity target,
            int maxHit,
            double xpMultiplier,
            PrayerModifiers prayers,
            GearBonus gear,
            DamageCalculator.Params params) {
        CombatStats stats = combat.stats(attacker);
        var meleeAttacker = new DamageCalculator.Attacker(
                stats.attack(), stats.strength(), gear.attackBonus(), gear.strengthBonus(),
                stats.buffs().attackBoost() + prayers.attackBoost(),
                stats.buffs().strengthBoost() + prayers.strengthBoost());
        var defender = defenderFor(target);
        YapSched.entity(plugin, attacker, () -> CombatHitResolver.resolveAsync(
                plugin,
                random -> DamageCalculator.roll(meleeAttacker, defender, params, random),
                result -> YapSched.entity(plugin, attacker, () -> applyResult(attacker, target, result, CombatStyle.MELEE,
                        () -> xp.awardDamageDealt(attacker.getUniqueId(), result.damage(), CombatStyle.MELEE)))));
        return true;
    }

    private void applyResult(
            Player attacker,
            LivingEntity target,
            DamageCalculator.Result result,
            CombatStyle style,
            Runnable xpAward) {
        if (!result.hit()) {
            attacker.sendActionBar(net.kyori.adventure.text.Component.text("§7Your ability misses."));
            return;
        }
        if (target instanceof Player tp) {
            YapSched.entity(plugin, tp, () -> {
                boolean dead = combat.applyDamage(tp, result.damage(), style);
                CombatPhysics.applyKnockback(tp, attacker, result, combat.config().physics());
                xpAward.run();
                if (dead) {
                    tp.setHealth(0);
                }
            });
        } else {
            YapSched.entity(plugin, target, () -> {
                double next = Math.max(0, target.getHealth() - result.damage());
                target.setHealth(next);
                CombatPhysics.applyKnockback(target, attacker, result, combat.config().physics());
            });
            xpAward.run();
        }
        String crit = result.critical() ? " §c§lCRIT" : "";
        attacker.sendActionBar(net.kyori.adventure.text.Component.text(
                "§c" + result.damage() + " damage" + crit));
    }

    private DamageCalculator.Defender defenderFor(LivingEntity target) {
        if (target instanceof Player tp) {
            CombatStats stats = combat.stats(tp);
            GearBonus gear = stats.gear();
            PrayerModifiers prayers = combat.prayerModifiers(tp);
            return new DamageCalculator.Defender(
                    stats.defence(),
                    gear.defenceBonus(),
                    stats.buffs().defenceBoost() + prayers.defenceBoost());
        }
        int defence = Math.max(1, (int) (target.getMaxHealth() / 8));
        return new DamageCalculator.Defender(defence, 0, 0);
    }

    private static CombatStyle parseStyle(String style) {
        if (style == null) {
            return CombatStyle.MAGIC;
        }
        return switch (style.toLowerCase()) {
            case "melee" -> CombatStyle.MELEE;
            case "ranged" -> CombatStyle.RANGED;
            default -> CombatStyle.MAGIC;
        };
    }
}
