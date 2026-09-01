package com.yapcore.abilities.exec;

import com.yapcore.abilities.AbilityDefinition;
import com.yapcore.abilities.AbilityEffect;
import com.yapcore.abilities.EffectKind;
import com.yapcore.abilities.ProjectileSpec;
import com.yapcore.sched.YapSched;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
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
            // Hide vanilla projectile body — ItemDisplay spell model replaces it.
            if (spec.hideEntity()) {
                entity.setVisibleByDefault(false);
                entity.setSilent(true);
            }
            ItemDisplay body = AbilityGraphics.attachProjectileBody(
                    plugin, entity, ability, spec.displayScale());
            UUID lockId = initialTarget != null ? initialTarget.getUniqueId() : null;
            active.put(entity.getUniqueId(), new Tracked(
                    caster.getUniqueId(), ability, spec, onHit, 0, lockId, body));
            track(entity);
        });
    }

    private void track(Entity projectile) {
        YapSched.globalLater(plugin, () -> tick(projectile), 1L);
    }

    private void tick(Entity projectile) {
        Tracked tracked = active.get(projectile.getUniqueId());
        if (tracked == null || !projectile.isValid()) {
            cleanup(projectile.getUniqueId(), tracked);
            return;
        }
        Tracked current = tracked.nextTick();
        active.put(projectile.getUniqueId(), current);
        AbilityGraphics.tickProjectileBody(current.body, projectile, current.ticks);
        if (current.spec.hasTrail() && current.ticks % current.spec.trailInterval() == 0) {
            spawnTrail(projectile.getLocation(), current.spec);
        }
        Player caster = plugin.getServer().getPlayer(current.casterId);
        if (caster == null) {
            cleanup(projectile.getUniqueId(), current);
            projectile.remove();
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
            impactBurst(projectile.getLocation(), current);
            cleanup(projectile.getUniqueId(), current);
            projectile.remove();
            return;
        }
        track(projectile);
    }

    private void finishHit(Entity projectile, Player caster, Tracked current, LivingEntity hit) {
        active.remove(projectile.getUniqueId());
        var onHit = current.onHit;
        Location impact = hit.getLocation().add(0, hit.getHeight() * 0.5, 0);
        impactBurst(impact, current);
        AbilityGraphics.removeDisplay(current.body);
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
            center.getWorld().spawnParticle(particle, center, 24, current.spec.splashRadius() * 0.45, 0.35,
                    current.spec.splashRadius() * 0.45, 0.02);
        }
        VfxEmitter.emitAt(plugin, center, new AbilityEffect(EffectKind.VFX, java.util.Map.of(
                "particle", current.spec.trailParticle().isBlank() ? "EXPLOSION" : current.spec.trailParticle(),
                "shape", "nova",
                "count", "18",
                "radius", String.valueOf(Math.max(1.0, current.spec.splashRadius())),
                "offset-y", "0.2")));
    }

    private void impactBurst(Location at, Tracked current) {
        String particle = current.spec.hasTrail() ? current.spec.trailParticle() : "CRIT";
        VfxEmitter.emitAt(plugin, at, new AbilityEffect(EffectKind.VFX, java.util.Map.of(
                "particle", particle,
                "shape", "nova",
                "count", "14",
                "radius", "0.9",
                "offset-y", "0.1")));
        VfxEmitter.emitAt(plugin, at, new AbilityEffect(EffectKind.SOUND, java.util.Map.of(
                "sound", "ENTITY_GENERIC_EXPLODE",
                "volume", "0.45",
                "pitch", "1.35")));
    }

    private void cleanup(UUID id, Tracked tracked) {
        active.remove(id);
        if (tracked != null) {
            AbilityGraphics.removeDisplay(tracked.body);
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
        for (Entity nearby : projectile.getNearbyEntities(0.85, 0.85, 0.85)) {
            if (!(nearby instanceof LivingEntity living)) {
                continue;
            }
            if (living.equals(caster) || living instanceof ItemDisplay) {
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
        int count = Math.max(spec.trailCount(), 4);
        location.getWorld().spawnParticle(particle, location, count, 0.08, 0.08, 0.08, 0.015);
        // Soft dust halo for readability
        location.getWorld().spawnParticle(Particle.DUST, location, 2, 0.05, 0.05, 0.05, 0,
                new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 220, 120), 0.9f));
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
            return switch (raw.trim().toUpperCase()) {
                case "BLOCK_CRACK", "BLOCK_DUST" -> Particle.BLOCK;
                default -> null;
            };
        }
    }

    private record Tracked(
            UUID casterId,
            AbilityDefinition ability,
            ProjectileSpec spec,
            BiConsumer<Player, LivingEntity> onHit,
            int ticks,
            UUID lockTargetId,
            ItemDisplay body) {

        Tracked nextTick() {
            return new Tracked(casterId, ability, spec, onHit, ticks + 1, lockTargetId, body);
        }
    }
}
