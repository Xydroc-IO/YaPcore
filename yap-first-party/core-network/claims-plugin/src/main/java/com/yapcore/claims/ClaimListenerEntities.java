package com.yapcore.claims;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.projectiles.ProjectileSource;

/** Entity damager / spawn-reason helpers for claim listeners. */
final class ClaimListenerEntities {

    private ClaimListenerEntities() {
    }

    static Player resolvePlayerDamager(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return player;
            }
        }
        if (damager instanceof Tameable tameable && tameable.getOwner() instanceof Player owner) {
            return owner;
        }
        return null;
    }

    static boolean isIntentionalSpawnReason(CreatureSpawnEvent.SpawnReason reason) {
        return reason == CreatureSpawnEvent.SpawnReason.CUSTOM
                || reason == CreatureSpawnEvent.SpawnReason.COMMAND
                || reason == CreatureSpawnEvent.SpawnReason.SPAWNER_EGG
                || reason == CreatureSpawnEvent.SpawnReason.SPAWNER;
    }

    static boolean intentionalSpawn(Entity entity) {
        try {
            var reason = entity.getEntitySpawnReason();
            return reason != null && isIntentionalSpawnReason(reason);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
