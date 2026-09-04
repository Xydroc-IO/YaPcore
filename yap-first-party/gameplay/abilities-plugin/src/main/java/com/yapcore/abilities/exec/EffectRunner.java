package com.yapcore.abilities.exec;

import com.yapcore.abilities.AbilityCombatServices;
import com.yapcore.abilities.AbilityDefinition;
import com.yapcore.abilities.AbilityEffect;
import com.yapcore.abilities.EffectKind;
import com.yapcore.abilities.StatusEffectService;
import com.yapcore.mmo.CombatServices;
import com.yapcore.mmo.CombatStats;
import com.yapcore.mmo.SkillId;
import com.yapcore.mmo.SkillServices;
import com.yapcore.mmo.XpSource;
import com.yapcore.sched.YapSched;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class EffectRunner {

    private final JavaPlugin plugin;
    private final StatusEffectService statusEffects;

    public EffectRunner(JavaPlugin plugin, StatusEffectService statusEffects) {
        this.plugin = plugin;
        this.statusEffects = statusEffects;
    }

    public void runCast(Player caster, AbilityDefinition ability) {
        AbilityGraphics.spawnCastIcon(plugin, caster, ability);
        runCast(caster, ability.castEffects(), ability);
    }

    public void runCast(Player caster, List<AbilityEffect> effects, AbilityDefinition ability) {
        runSequence(caster, caster, effects, 0, true, ability);
    }

    public void runHit(Player caster, LivingEntity target, List<AbilityEffect> effects, AbilityDefinition ability) {
        runSequence(caster, target, effects, 0, false, ability);
    }

    public void runAmbient(LivingEntity target, List<AbilityEffect> effects, AbilityDefinition ability) {
        runSequence(null, target, effects, 0, false, ability);
    }

    /** Sequential effects with DELAY support (ticks before continuing the list). */
    private void runSequence(
            Player caster,
            LivingEntity target,
            List<AbilityEffect> effects,
            int index,
            boolean castPhase,
            AbilityDefinition ability) {
        if (effects == null || index >= effects.size()) {
            return;
        }
        AbilityEffect effect = effects.get(index);
        if (effect.kind() == EffectKind.DELAY) {
            long ticks = Math.max(1, effect.intParam("ticks", 5));
            LivingEntity scheduleOn = target != null ? target : caster;
            if (scheduleOn != null) {
                YapSched.entityLater(plugin, scheduleOn,
                        () -> runSequence(caster, target, effects, index + 1, castPhase, ability),
                        ticks);
            } else {
                YapSched.globalLater(plugin,
                        () -> runSequence(caster, target, effects, index + 1, castPhase, ability),
                        ticks);
            }
            return;
        }
        runSingle(caster, target, effect, castPhase, ability);
        runSequence(caster, target, effects, index + 1, castPhase, ability);
    }

    private void runSingle(
            Player caster,
            LivingEntity target,
            AbilityEffect effect,
            boolean castPhase,
            AbilityDefinition ability) {
        switch (effect.kind()) {
            case VFX, SOUND -> {
                if (castPhase && caster != null) {
                    VfxEmitter.runCast(plugin, caster, effect);
                } else if (target != null) {
                    VfxEmitter.runHit(plugin, target, effect);
                }
            }
            case DAMAGE -> {
                if (caster != null && target != null) {
                    applyDamage(caster, target, effect);
                } else if (target != null) {
                    fallbackDamage(target, effect.intParam("max-hit", 2));
                }
            }
            case HEAL -> applyHeal(caster, target, effect);
            case BUFF, DEBUFF -> {
                if (caster != null && target != null) {
                    applyStatus(caster, target, effect);
                }
            }
            case KNOCKBACK -> {
                if (caster != null && target != null) {
                    applyKnockback(caster, target, effect);
                }
            }
            case XP -> {
                if (caster != null) {
                    awardXp(caster, effect, castPhase);
                }
            }
            case DRAIN_PRAYER -> {
                if (caster != null) {
                    drainPrayer(caster, effect);
                }
            }
            case TELEPORT -> {
                if (caster != null) {
                    teleportForward(caster, effect);
                }
            }
            case VELOCITY -> applyVelocity(target, effect);
            case ANIMATION -> {
                if (caster != null) {
                    AnimationSync.play(plugin, caster, effect);
                }
            }
            case DISPLAY -> {
                if (caster != null && ability != null) {
                    AbilityGraphics.spawnCastIcon(plugin, caster, ability);
                }
            }
            case AOE -> applyAoe(caster, target, effect, ability);
            case CHAIN -> applyChain(caster, target, effect);
            case DELAY -> {
                // Handled by runSequence
            }
        }
    }

    private void applyAoe(Player caster, LivingEntity anchor, AbilityEffect effect, AbilityDefinition ability) {
        if (caster == null || ability == null) {
            return;
        }
        double radius = effect.doubleParam("radius", ability.range());
        Location center = anchor != null
                ? anchor.getLocation()
                : AoeHelper.areaCenter(caster, ability, null);
        List<LivingEntity> targets = AoeHelper.targetsAt(caster, center, ability, radius);
        List<AbilityEffect> nested = List.of(
                new AbilityEffect(EffectKind.DAMAGE, effect.params()),
                new AbilityEffect(EffectKind.VFX, java.util.Map.of(
                        "particle", effect.param("particle", "EXPLOSION"),
                        "count", "8")));
        for (LivingEntity victim : targets) {
            runHit(caster, victim, nested, ability);
        }
        VfxEmitter.emitAt(plugin, center, new AbilityEffect(EffectKind.VFX, java.util.Map.of(
                "particle", effect.param("particle", "EXPLOSION"),
                "shape", effect.param("shape", "nova"),
                "count", String.valueOf(effect.intParam("count", 16)),
                "radius", String.valueOf(radius),
                "spread", String.valueOf(effect.doubleParam("spread", radius * 0.35)))));
    }

    private void applyChain(Player caster, LivingEntity origin, AbilityEffect effect) {
        if (caster == null || origin == null) {
            return;
        }
        int jumps = effect.intParam("jumps", 3);
        double radius = effect.doubleParam("radius", 5.0);
        chainJump(caster, origin, effect, jumps, radius, new HashSet<>());
    }

    private void chainJump(
            Player caster,
            LivingEntity from,
            AbilityEffect effect,
            int remaining,
            double radius,
            Set<java.util.UUID> visited) {
        if (remaining <= 0 || !visited.add(from.getUniqueId())) {
            return;
        }
        applyDamage(caster, from, effect);
        VfxEmitter.runHit(plugin, from, new AbilityEffect(EffectKind.VFX, java.util.Map.of(
                "particle", "ELECTRIC_SPARK",
                "count", "6")));
        LivingEntity next = null;
        double best = Double.MAX_VALUE;
        for (org.bukkit.entity.Entity entity : from.getNearbyEntities(radius, radius, radius)) {
            if (!(entity instanceof LivingEntity candidate)) {
                continue;
            }
            if (candidate.equals(caster) || visited.contains(candidate.getUniqueId())) {
                continue;
            }
            double dist = candidate.getLocation().distanceSquared(from.getLocation());
            if (dist < best) {
                best = dist;
                next = candidate;
            }
        }
        if (next != null) {
            LivingEntity chainTarget = next;
            YapSched.entityLater(plugin, chainTarget,
                    () -> chainJump(caster, chainTarget, effect, remaining - 1, radius, visited), 2L);
        }
    }

    private void applyDamage(Player caster, LivingEntity target, AbilityEffect effect) {
        String style = effect.param("style", "magic").toLowerCase();
        int maxHit = effect.intParam("max-hit", 4);
        double xpMult = effect.doubleParam("xp-multiplier", 2.0);
        AbilityCombatServices.find().ifPresentOrElse(
                bridge -> bridge.applyDamage(caster, target, style, maxHit, xpMult),
                () -> fallbackDamage(target, maxHit));
    }

    private static void fallbackDamage(LivingEntity target, int maxHit) {
        double dmg = Math.max(1, maxHit / 2.0);
        double next = Math.max(0, target.getHealth() - dmg);
        target.setHealth(next);
    }

    private void applyHeal(Player caster, LivingEntity target, AbilityEffect effect) {
        int amount = effect.intParam("amount", 4);
        if (target instanceof Player player && CombatServices.find().isPresent()) {
            YapSched.entity(plugin, player, () -> {
                CombatStats stats = CombatServices.find().get().stats(player);
                int max = stats.maxHp();
                int next = Math.min(max, stats.currentHp() + amount);
                CombatServices.find().get().setHp(player.getUniqueId(), next);
            });
            return;
        }
        YapSched.entity(plugin, target, () -> {
            double next = Math.min(target.getMaxHealth(), target.getHealth() + amount);
            target.setHealth(next);
        });
    }

    private void applyStatus(Player caster, LivingEntity target, AbilityEffect effect) {
        String id = effect.param("id", effect.param("effect", ""));
        if (id.isBlank()) {
            return;
        }
        int stacks = effect.intParam("stacks", 1);
        statusEffects.apply(target, id, caster.getUniqueId(), stacks);
    }

    private void applyKnockback(Player caster, LivingEntity target, AbilityEffect effect) {
        double power = effect.doubleParam("power", 0.35);
        YapSched.entity(plugin, target, () -> {
            Vector dir = target.getLocation().toVector().subtract(caster.getLocation().toVector());
            if (dir.lengthSquared() < 0.001) {
                dir = caster.getLocation().getDirection();
            }
            dir.normalize().multiply(power).setY(Math.max(0.12, dir.getY() + 0.15));
            target.setVelocity(dir);
        });
    }

    private void awardXp(Player caster, AbilityEffect effect, boolean castPhase) {
        boolean onCast = effect.boolParam("on-cast", false);
        if (onCast != castPhase) {
            return;
        }
        String skill = effect.param("skill", "magic");
        double amount = effect.doubleParam("amount", 10);
        SkillServices.find().ifPresent(skills ->
                skills.addXp(caster.getUniqueId(), SkillId.of(skill), amount, XpSource.ACTION));
    }

    private void drainPrayer(Player caster, AbilityEffect effect) {
        int amount = effect.intParam("amount", 1);
        AbilityCombatServices.find().ifPresent(b -> b.drainPrayer(caster, amount));
    }

    private void teleportForward(Player caster, AbilityEffect effect) {
        double distance = effect.doubleParam("distance", 6.0);
        YapSched.entity(plugin, caster, () -> {
            var dest = caster.getLocation().add(caster.getLocation().getDirection().normalize().multiply(distance));
            caster.teleportAsync(dest);
        });
    }

    private void applyVelocity(LivingEntity target, AbilityEffect effect) {
        if (target == null) {
            return;
        }
        double x = effect.doubleParam("x", 0);
        double y = effect.doubleParam("y", 0.2);
        double z = effect.doubleParam("z", 0);
        YapSched.entity(plugin, target, () -> target.setVelocity(new Vector(x, y, z)));
    }
}
