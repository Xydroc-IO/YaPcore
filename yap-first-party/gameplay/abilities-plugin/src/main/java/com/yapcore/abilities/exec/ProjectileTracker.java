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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

public final class ProjectileTracker {

    private final JavaPlugin plugin;
    private final EffectRunner effects;
    private final ConcurrentHashMap<UUID, Tracked> active = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, AtomicInteger> perPlayer = new ConcurrentHashMap<>();

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
        int maxPer = plugin.getConfig().getInt("folia-safe.max-projectiles-per-player", 12);
        int maxGlobal = plugin.getConfig().getInt("folia-safe.max-projectiles-global", 96);
        AtomicInteger mine = perPlayer.computeIfAbsent(caster.getUniqueId(), u -> new AtomicInteger());
        if (mine.get() >= maxPer || active.size() >= maxGlobal) {
            // Still apply hit if we already have a lock target — skip only the projectile FX.
            if (initialTarget != null) {
                onHit.accept(caster, initialTarget);
            } else {
                caster.sendActionBar(net.kyori.adventure.text.Component.text(
                        "§7Too many spells in flight — try again"));
            }
            return;
        }
        YapSched.entity(plugin, caster, () -> {
            if (mine.get() >= maxPer || active.size() >= maxGlobal) {
                if (initialTarget != null) {
                    onHit.accept(caster, initialTarget);
                }
                return;
            }
            EntityType type = parseEntityType(spec.entityType());
            if (type == null) {
                if (initialTarget != null) {
                    onHit.accept(caster, initialTarget);
                }
                return;
            }
            Location spawn = caster.getEyeLocation();
            Vector direction = spawn.getDirection().normalize();
            Vector velocity = direction.clone().multiply(spec.speed());
            if (spec.isArc() && !spec.isHoming()) {
                // Seed upward so early ticks still look alive before path override.
                velocity.setY(velocity.getY() + Math.min(0.55, spec.arcHeight() * 0.18));
            }
            Entity entity;
            try {
                entity = caster.getWorld().spawnEntity(spawn, type);
            } catch (RuntimeException ex) {
                plugin.getLogger().fine("projectile spawn skipped: " + ex.getMessage());
                return;
            }
            if (entity instanceof Projectile projectile) {
                projectile.setShooter(caster);
                projectile.setVelocity(velocity);
            } else {
                entity.setVelocity(velocity);
            }
            // Hide vanilla projectile body — ItemDisplay spell model replaces it.
            if (spec.hideEntity()) {
                hideProjectileCarrier(caster, entity);
            }
            ItemDisplay body = null;
            try {
                float scale = spec.displayScale() <= 0 ? 1.2f : Math.max(spec.displayScale(), 1.05f);
                body = AbilityGraphics.attachProjectileBody(
                        plugin, entity, ability, scale);
            } catch (RuntimeException ex) {
                plugin.getLogger().fine("projectile body skipped: " + ex.getMessage());
            }
            UUID lockId = initialTarget != null ? initialTarget.getUniqueId() : null;
            active.put(entity.getUniqueId(), new Tracked(
                    caster.getUniqueId(),
                    ability,
                    spec,
                    onHit,
                    0,
                    lockId,
                    body,
                    spawn.clone(),
                    direction.clone(),
                    spawn.clone()));
            mine.incrementAndGet();
            track(entity);
        });
    }

    private void track(Entity projectile) {
        YapSched.entityLater(plugin, projectile, () -> tick(projectile), 1L);
    }

    private void tick(Entity projectile) {
        Tracked tracked = active.get(projectile.getUniqueId());
        if (tracked == null || !projectile.isValid()) {
            cleanup(projectile.getUniqueId(), tracked);
            return;
        }
        Tracked current = tracked.nextTick(projectile.getLocation());
        active.put(projectile.getUniqueId(), current);
        // Passenger bodies need no cross-entity scheduler hop — spin in-place only.
        if (current.body != null && current.body.isValid()) {
            AbilityGraphics.tickProjectileBody(current.body, projectile, current.ticks);
        }
        if (current.spec.isArc() && !current.spec.isHoming()) {
            applyArc(projectile, current);
        }
        if (current.spec.hasTrail() && current.ticks % current.spec.trailInterval() == 0) {
            spawnTrail(projectile.getLocation(), current);
        }
        Player caster = plugin.getServer().getPlayer(current.casterId);
        if (caster == null) {
            cleanup(projectile.getUniqueId(), current);
            safeRemove(projectile);
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
            safeRemove(projectile);
            return;
        }
        track(projectile);
    }

    private void applyArc(Entity projectile, Tracked current) {
        ProjectileSpec spec = current.spec;
        double max = Math.max(1, spec.maxTicks());
        double t = Math.min(1.0, current.ticks / max);
        double travel = spec.speed() * current.ticks;
        Vector dir = current.direction.clone();
        Location next = current.origin.clone().add(dir.clone().multiply(travel));
        double peak = spec.arcHeight() > 0 ? spec.arcHeight() : Math.max(1.2, travel * 0.12);
        // Parabola peaking mid-flight, layered on the aim pitch.
        next.setY(current.origin.getY() + dir.getY() * travel + peak * 4.0 * t * (1.0 - t));
        Vector delta = next.toVector().subtract(projectile.getLocation().toVector());
        if (delta.lengthSquared() < 0.0001) {
            return;
        }
        // Cap velocity so Folia entity ticks stay stable.
        double mag = Math.min(3.5, Math.max(0.15, delta.length()));
        projectile.setVelocity(delta.normalize().multiply(mag));
    }

    private void finishHit(Entity projectile, Player caster, Tracked current, LivingEntity hit) {
        active.remove(projectile.getUniqueId());
        releasePlayerSlot(current.casterId);
        var onHit = current.onHit;
        Location impact = hit.getLocation().add(0, hit.getHeight() * 0.5, 0);
        impactBurst(impact, current);
        AbilityGraphics.removeDisplay(plugin, current.body);
        YapSched.entity(plugin, hit, () -> {
            try {
                onHit.accept(caster, hit);
                if (current.spec.hasSplash()) {
                    splash(hit.getLocation(), caster, current, hit);
                }
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("ability on-hit failed: " + ex.getMessage());
            }
            safeRemove(projectile);
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
                "shape", "shockwave",
                "count", "22",
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
        // Staged secondary burst a tick later for weight.
        YapSched.regionChunkLater(plugin, at.getWorld(), at.getBlockX() >> 4, at.getBlockZ() >> 4, () ->
                VfxEmitter.emitAt(plugin, at, new AbilityEffect(EffectKind.VFX, java.util.Map.of(
                        "particle", particle,
                        "shape", "orb",
                        "count", "10",
                        "radius", "0.55",
                        "offset-y", "0.15"))), 2L);
        VfxEmitter.emitAt(plugin, at, new AbilityEffect(EffectKind.SOUND, java.util.Map.of(
                "sound", "ENTITY_GENERIC_EXPLODE",
                "volume", "0.45",
                "pitch", "1.35")));
        if (current.spec.impactShake()) {
            ImpactFx.shakeAt(plugin, at, current.spec.shakePower(), 3, Math.max(2.5, current.spec.splashRadius()));
        }
    }

    private void cleanup(UUID id, Tracked tracked) {
        Tracked removed = active.remove(id);
        Tracked t = tracked != null ? tracked : removed;
        if (t != null) {
            releasePlayerSlot(t.casterId());
            AbilityGraphics.removeDisplay(plugin, t.body());
        }
    }

    private void releasePlayerSlot(UUID casterId) {
        AtomicInteger mine = perPlayer.get(casterId);
        if (mine != null) {
            mine.updateAndGet(v -> Math.max(0, v - 1));
        }
    }

    private void safeRemove(Entity entity) {
        if (entity == null || !entity.isValid()) {
            return;
        }
        YapSched.entity(plugin, entity, () -> {
            if (entity.isValid()) {
                entity.remove();
            }
        });
    }

    /** Called when vanilla removes the projectile (block hit, despawn, etc.). */
    public void onProjectileGone(Entity projectile) {
        if (projectile == null) {
            return;
        }
        Tracked tracked = active.remove(projectile.getUniqueId());
        if (tracked != null) {
            releasePlayerSlot(tracked.casterId());
            AbilityGraphics.removeDisplay(plugin, tracked.body());
        }
    }

    public boolean isTracked(Entity projectile) {
        return projectile != null && active.containsKey(projectile.getUniqueId());
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

    private static void spawnTrail(Location location, Tracked tracked) {
        ProjectileSpec spec = tracked.spec;
        Particle particle = parseParticle(spec.trailParticle());
        if (particle == null || location.getWorld() == null) {
            return;
        }
        double life = Math.min(1.0, tracked.ticks / (double) Math.max(1, spec.maxTicks()));
        int baseCount = Math.max(spec.trailCount(), 2);
        int count = Math.max(1, (int) Math.round(baseCount * (1.0 - spec.trailFalloff() * life)));
        Vector motion = location.toVector().subtract(tracked.lastLoc.toVector());
        String style = spec.trailStyle();
        if (("motion".equals(style) || "ribbon".equals(style)) && motion.lengthSquared() > 0.0004) {
            Vector step = motion.clone().multiply(1.0 / Math.max(1, count));
            Vector side = motion.clone().crossProduct(new Vector(0, 1, 0));
            if (side.lengthSquared() < 0.0001) {
                side = motion.clone().crossProduct(new Vector(1, 0, 0));
            }
            if (side.lengthSquared() > 0.0001) {
                side.normalize().multiply("ribbon".equals(style) ? 0.12 : 0.04);
            } else {
                side = new Vector(0, 0, 0);
            }
            for (int i = 0; i < count; i++) {
                Location p = tracked.lastLoc.clone().add(step.clone().multiply(i)).add(side.clone().multiply((i % 2 == 0) ? 1 : -1));
                location.getWorld().spawnParticle(particle, p, 1, 0.01, 0.01, 0.01, 0.0);
            }
        } else {
            location.getWorld().spawnParticle(particle, location, count, 0.08, 0.08, 0.08, 0.015);
        }
        // Soft dust halo for readability (brighter + denser)
        location.getWorld().spawnParticle(Particle.DUST, location, 5, 0.08, 0.08, 0.08, 0,
                new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 230, 140), 1.15f));
        location.getWorld().spawnParticle(Particle.END_ROD, location, 1, 0.02, 0.02, 0.02, 0.0);
    }

    private void hideProjectileCarrier(Player caster, Entity entity) {
        entity.setVisibleByDefault(false);
        entity.setSilent(true);
        if (entity instanceof LivingEntity living) {
            living.setInvisible(true);
            living.setCollidable(false);
        }
        // Hide from every online viewer (including caster) — setVisibleByDefault alone
        // is not enough on all client/proxy paths and left EGG/SNOWBALL bodies visible.
        for (Player viewer : plugin.getServer().getOnlinePlayers()) {
            try {
                viewer.hideEntity(plugin, entity);
            } catch (RuntimeException ignored) {
            }
        }
        try {
            caster.hideEntity(plugin, entity);
        } catch (RuntimeException ignored) {
        }
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
            ItemDisplay body,
            Location origin,
            Vector direction,
            Location lastLoc) {

        Tracked nextTick(Location now) {
            return new Tracked(casterId, ability, spec, onHit, ticks + 1, lockTargetId, body,
                    origin, direction, now == null ? lastLoc : now.clone());
        }
    }
}
