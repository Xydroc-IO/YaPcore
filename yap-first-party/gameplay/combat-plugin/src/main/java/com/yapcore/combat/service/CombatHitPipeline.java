package com.yapcore.combat.service;

import com.yapcore.combat.CombatConfig;
import com.yapcore.combat.combo.ComboService;
import com.yapcore.combat.formula.CombatAttackGate;
import com.yapcore.combat.formula.CombatHitResolver;
import com.yapcore.combat.formula.CombatPhysics;
import com.yapcore.combat.formula.DamageCalculator;
import com.yapcore.combat.prayer.PrayerModifiers;
import com.yapcore.combat.status.StatusEffectService;
import com.yapcore.mmo.CombatBuffs;
import com.yapcore.mmo.CombatStats;
import com.yapcore.mmo.CombatStyle;
import com.yapcore.mmo.GearBonus;
import com.yapcore.sched.YapSched;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ThreadLocalRandom;

public final class CombatHitPipeline {

    private final JavaPlugin plugin;
    private final CombatConfig config;
    private final CombatServiceImpl combat;
    private final CombatXpAwarder xp;
    private final StatusEffectService status;
    private final ComboService combo;
    private final CombatAttackGate attackGate;

    public CombatHitPipeline(
            JavaPlugin plugin,
            CombatConfig config,
            CombatServiceImpl combat,
            CombatXpAwarder xp,
            StatusEffectService status,
            ComboService combo,
            CombatAttackGate attackGate) {
        this.plugin = plugin;
        this.config = config;
        this.combat = combat;
        this.xp = xp;
        this.status = status;
        this.combo = combo;
        this.attackGate = attackGate;
    }

    public void beginPlayerAttack(
            Player attacker,
            LivingEntity victim,
            CombatStyle style,
            HitModifiers modifiers) {
        if (status.blocksAttacks(attacker)) {
            attacker.sendActionBar(Component.text("§8You are stunned!"));
            return;
        }
        if (!attackGate.tryAcquire(attacker, style, config.attackCooldownTicks(), config.rangedCooldownTicks())) {
            return;
        }
        YapSched.entity(plugin, attacker, () -> {
            CombatStats atkStats = combat.stats(attacker);
            PrayerModifiers atkPrayers = combat.prayerModifiers(attacker);
            var atkStatus = status.modifiers(attacker);
            DamageCalculator.Defender defender = buildDefender(victim);
            DamageCalculator.Params params = combat.formulaParams();
            CombatHitResolver.resolveAsync(
                    plugin,
                    random -> roll(style, atkStats, atkPrayers, atkStatus, defender, params, random),
                    result -> YapSched.entity(plugin, attacker, () -> {
                        if (!result.hit()) {
                            combo.recordMiss(attacker);
                            sendMiss(attacker, victim);
                            return;
                        }
                        applySuccessfulHit(attacker, victim, style, result, modifiers);
                    }));
        });
    }

    public void beginMobAttack(LivingEntity mob, Player victim) {
        int mobStrength = Math.max(1, (int) mob.getMaxHealth() / 5);
        var attacker = new DamageCalculator.Attacker(mobStrength, mobStrength, 0, 0, 0, 0);
        CombatStats stats = combat.stats(victim);
        PrayerModifiers prayers = combat.prayerModifiers(victim);
        var defStatus = status.modifiers(victim);
        DamageCalculator.Params params = combat.formulaParams();
        CombatHitResolver.resolveAsync(
                plugin,
                random -> DamageCalculator.roll(
                        attacker,
                        toDefender(stats, prayers, defStatus),
                        params,
                        random),
                result -> YapSched.entity(plugin, victim, () -> {
                    if (!result.hit()) {
                        return;
                    }
                    int damage = status.scaleIncomingDamage(result.damage(), victim);
                    boolean dead = combat.applyDamage(victim, damage, CombatStyle.MELEE);
                    CombatPhysics.applyKnockback(victim, mob, result, config.physics());
                    xp.awardDamageTaken(victim.getUniqueId(), damage);
                    if (dead) {
                        victim.setHealth(0);
                    }
                }));
    }

    private void applySuccessfulHit(
            Player attacker,
            LivingEntity victim,
            CombatStyle style,
            DamageCalculator.Result result,
            HitModifiers modifiers) {
        int scaled = status.scaleOutgoingDamage(result.damage(), attacker);
        scaled = (int) Math.ceil(scaled * modifiers.dropOffMultiplier());
        if (modifiers.headshot()) {
            scaled = (int) Math.ceil(scaled * config.projectiles().headshotMultiplier());
        }
        ComboService.HitResult comboHit = combo.recordHit(attacker, victim, scaled);
        int finalDamage = status.scaleIncomingDamage(comboHit.damage(), victim);
        procOnHit(attacker, victim, result, style);
        if (victim instanceof Player playerVictim) {
            YapSched.entity(plugin, playerVictim, () -> {
                boolean dead = combat.applyDamage(playerVictim, finalDamage, style);
                CombatPhysics.applyKnockback(playerVictim, attacker, result, config.physics());
                if (dead) {
                    playerVictim.setHealth(0);
                }
            });
        } else {
            YapSched.entity(plugin, victim, () -> {
                double next = Math.max(0, victim.getHealth() - finalDamage);
                victim.setHealth(next);
                if (next <= 0 && !victim.isDead()) {
                    victim.setHealth(0);
                }
                CombatPhysics.applyKnockback(victim, attacker, result, config.physics());
            });
        }
        xp.awardDamageDealt(attacker.getUniqueId(), finalDamage, style);
        showHitFeedback(attacker, result, comboHit, modifiers);
    }

    private void procOnHit(Player attacker, LivingEntity victim, DamageCalculator.Result result, CombatStyle style) {
        for (CombatConfig.OnHitProc proc : config.onHitProcs()) {
            if (!proc.matches(result, style)) {
                continue;
            }
            if (ThreadLocalRandom.current().nextDouble() <= proc.chance()) {
                status.apply(victim, proc.effectId(), attacker, proc.stacks());
            }
        }
    }

    private DamageCalculator.Defender buildDefender(LivingEntity victim) {
        if (victim instanceof Player player) {
            CombatStats stats = combat.stats(player);
            PrayerModifiers prayers = combat.prayerModifiers(player);
            return toDefender(stats, prayers, status.modifiers(player));
        }
        return mobDefender(victim);
    }

    private static DamageCalculator.Result roll(
            CombatStyle style,
            CombatStats atkStats,
            PrayerModifiers atkPrayers,
            com.yapcore.combat.status.StatusModifiers atkStatus,
            DamageCalculator.Defender defender,
            DamageCalculator.Params params,
            java.util.Random random) {
        if (style == CombatStyle.RANGED) {
            return DamageCalculator.rollRanged(
                    toRangedAttacker(atkStats, atkPrayers, atkStatus),
                    defender,
                    params,
                    random);
        }
        return DamageCalculator.roll(
                toAttacker(atkStats, atkPrayers, atkStatus),
                defender,
                params,
                random);
    }

    private void sendMiss(Player attacker, LivingEntity victim) {
        String name = victim instanceof Player p
                ? p.getName()
                : victim.getType().name().toLowerCase().replace('_', ' ');
        attacker.sendActionBar(Component.text("§7You miss " + name + "."));
    }

    private void showHitFeedback(
            Player attacker,
            DamageCalculator.Result result,
            ComboService.HitResult comboHit,
            HitModifiers modifiers) {
        StringBuilder sb = new StringBuilder();
        if (!comboHit.label().isEmpty()) {
            sb.append(comboHit.label()).append(" §8| ");
        }
        if (modifiers.headshot()) {
            sb.append("§6HEADSHOT §8| ");
        }
        if (result.critical()) {
            sb.append("§c§lCRIT §7").append(comboHit.damage())
                    .append(" §8/ §7").append(result.maxHit());
        } else {
            sb.append("§7Hit §c").append(comboHit.damage())
                    .append(" §8/ §7").append(result.maxHit());
        }
        attacker.sendActionBar(Component.text(sb.toString()));
    }

    private static DamageCalculator.Attacker toAttacker(
            CombatStats stats,
            PrayerModifiers prayers,
            com.yapcore.combat.status.StatusModifiers status) {
        CombatBuffs buffs = stats.buffs();
        GearBonus gear = stats.gear();
        return new DamageCalculator.Attacker(
                stats.attack(),
                stats.strength(),
                gear.attackBonus(),
                gear.strengthBonus(),
                buffs.attackBoost() + prayers.attackBoost() + status.attackBoost(),
                buffs.strengthBoost() + prayers.strengthBoost() + status.strengthBoost());
    }

    private static DamageCalculator.RangedAttacker toRangedAttacker(
            CombatStats stats,
            PrayerModifiers prayers,
            com.yapcore.combat.status.StatusModifiers status) {
        GearBonus gear = stats.gear();
        return new DamageCalculator.RangedAttacker(
                stats.ranged(),
                gear.rangedBonus(),
                prayers.rangedBoost() + status.attackBoost());
    }

    private static DamageCalculator.Defender toDefender(
            CombatStats stats,
            PrayerModifiers prayers,
            com.yapcore.combat.status.StatusModifiers status) {
        GearBonus gear = stats.gear();
        CombatBuffs buffs = stats.buffs();
        return new DamageCalculator.Defender(
                stats.defence(),
                gear.defenceBonus(),
                buffs.defenceBoost() + prayers.defenceBoost() + status.defenceBoost());
    }

    private static DamageCalculator.Defender mobDefender(LivingEntity mob) {
        int defence = Math.max(1, (int) (mob.getMaxHealth() / 8));
        return new DamageCalculator.Defender(defence, 0, 0);
    }

    public record HitModifiers(boolean headshot, double dropOffMultiplier) {
        public static HitModifiers none() {
            return new HitModifiers(false, 1.0);
        }
    }
}
