package com.yapcore.abilities.exec;

import com.yapcore.abilities.AbilityDefinition;
import com.yapcore.abilities.AbilityEffect;
import com.yapcore.abilities.ProjectileSpec;
import com.yapcore.sched.YapSched;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public final class ProjectileTracker {

    private final JavaPlugin plugin;
    private final EffectRunner effects;
    private final ConcurrentHashMap<UUID, Tracked> active = new ConcurrentHashMap<>();

    public ProjectileTracker(JavaPlugin plugin, EffectRunner effects) {
        this.plugin = plugin;
        this.effects = effects;
    }

    public void launch(
            Player caster,
            AbilityDefinition ability,
            LivingEntity initialTarget,
            BiConsumer<Player, LivingEntity> onHit) {
        ProjectileSpec spec = ability.projectile();
        if (spec == null) {
            if (initialTarget != null) {
                onHit.accept(caster, initialTarget);
            }
            return;
        }
        YapSched.entity(plugin, caster, () -> {
            EntityType type = parseEntityType(spec.entityType());
            if (type == null) {
                if (initialTarget != null) {
                    onHit.accept(caster, initialTarget);
                }
                return;
            }
            Location spawn = caster.getEyeLocation();
            Vector velocity = spawn.getDirection().normalize().multiply(spec.speed());
            Entity entity = caster.getWorld().spawnEntity(spawn, type);
            if (entity instanceof Projectile projectile) {
                projectile.setShooter(caster);
                projectile.setVelocity(velocity);
            } else {
                entity.setVelocity(velocity);
            }
            UUID lockId = initialTarget != null ? initialTarget.getUniqueId() : null;
            active.put(entity.getUniqueId(), new Tracked(
                    caster.getUniqueId(), ability, spec, onHit, 0, lockId));
            if (spec.iconCmd() > 0 || ability.resolvedIconCmd() > 0) {
                AbilityGraphics.spawnCastIcon(plugin, caster, ability);
            }
            track(entity);
        });
    }

    private void track(Entity projectile) {
        YapSched.globalLater(plugin, () -> tick(projectile), 1L);
    }

    private void tick(Entity projectile) {
        Tracked tracked = active.get(projectile.getUniqueId());
        if (tracked == null || !projectile.isValid()) {
            active.remove(projectile.getUniqueId());
            return;
        }
        Tracked current = tracked.nextTick();
        active.put(projectile.getUniqueId(), current);
        if (current.spec.hasTrail() && current.ticks % current.spec.trailInterval() == 0) {
            spawnTrail(projectile.getLocation(), current.spec);
        }
        Player caster = plugin.getServer().getPlayer(current.casterId);
        if (caster == null) {
            projectile.remove();
            active.remove(projectile.getUniqueId());
            return;
        }
        if (current.spec.isHoming()) {
            steer(projectile, caster, current.lockTargetId);
        }
        LivingEntity hit = findHit(projectile, caster);
        if (hit != null) {
            finishHit(projectile, caster, current, hit);
            return;
        }
        if (current.ticks >= current.spec.maxTicks()) {
            if (current.spec.hasSplash()) {
                splash(projectile.getLocation(), caster, current, null);
            }
            active.remove(projectile.getUniqueId());
            projectile.remove();
            return;
        }
        track(projectile);
    }

    private void finishHit(Entity projectile, Player caster, Tracked current, LivingEntity hit) {
        active.remove(projectile.getUniqueId());
        var onHit = current.onHit;
        YapSched.entity(plugin, hit, () -> {
            onHit.accept(caster, hit);
            if (current.spec.hasSplash()) {
                splash(hit.getLocation(), caster, current, hit);
            }
            projectile.remove();
        });
    }

    private void splash(Location center, Player caster, Tracked current, LivingEntity exclude) {
        List<LivingEntity> targets = AoeHelper.targetsAt(caster, center, current.ability, current.spec.splashRadius());
        for (LivingEntity target : targets) {
            if (exclude != null && target.getUniqueId().equals(exclude.getUniqueId())) {
                continue;
            }
            effects.runHit(caster, target, current.ability.hitEffects(), current.ability);
        }
        Particle particle = parseParticle(current.spec.trailParticle());
        if (particle != null) {
            center.getWorld().spawnParticle(particle, center, 16, current.spec.splashRadius() * 0.4, 0.2,
                    current.spec.splashRadius() * 0.4, 0.01);
        }
    }

    private void steer(Entity projectile, Player caster, UUID lockTargetId) {
        LivingEntity target = resolveHomingTarget(projectile, caster, lockTargetId);
        if (target == null) {
            return;
        }
        Vector to = target.getLocation().add(0, target.getHeight() * 0.5, 0)
                .toVector()
                .subtract(projectile.getLocation().toVector());
        if (to.lengthSquared() < 0.001) {
            return;
        }
        Tracked tracked = active.get(projectile.getUniqueId());
        double turn = tracked == null ? 0.15 : tracked.spec.turnRate();
        Vector desired = to.normalize().multiply(tracked == null ? 1.2 : tracked.spec.speed());
        Vector current = projectile.getVelocity();
        Vector blended = current.multiply(1.0 - turn).add(desired.multiply(turn));
        projectile.setVelocity(blended);
    }

    private LivingEntity resolveHomingTarget(Entity projectile, Player caster, UUID lockTargetId) {
        if (lockTargetId != null) {
            Entity locked = plugin.getServer().getEntity(lockTargetId);
            if (locked instanceof LivingEntity living && living.isValid() && !living.equals(caster)) {
                return living;
            }
        }
        LivingEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (Entity nearby : projectile.getNearbyEntities(12, 12, 12)) {
            if (!(nearby instanceof LivingEntity living) || living.equals(caster)) {
                continue;
            }
            double dist = living.getLocation().distanceSquared(projectile.getLocation());
            if (dist < best) {
                best = dist;
                nearest = living;
            }
        }
        return nearest;
    }

    private LivingEntity findHit(Entity projectile, Player caster) {
        for (Entity nearby : projectile.getNearbyEntities(0.75, 0.75, 0.75)) {
            if (!(nearby instanceof LivingEntity living)) {
                continue;
            }
            if (living.equals(caster)) {
                continue;
            }
            return living;
        }
        return null;
    }

    private static void spawnTrail(Location location, ProjectileSpec spec) {
        Particle particle = parseParticle(spec.trailParticle());
        if (particle == null) {
            return;
        }
        location.getWorld().spawnParticle(particle, location, spec.trailCount(), 0.05, 0.05, 0.05, 0.01);
    }

    private static EntityType parseEntityType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return EntityType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Particle parseParticle(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Particle.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private record Tracked(
            UUID casterId,
            AbilityDefinition ability,
            ProjectileSpec spec,
            BiConsumer<Player, LivingEntity> onHit,
            int ticks,
            UUID lockTargetId) {

        Tracked nextTick() {
            return new Tracked(casterId, ability, spec, onHit, ticks + 1, lockTargetId);
        }
    }
}
