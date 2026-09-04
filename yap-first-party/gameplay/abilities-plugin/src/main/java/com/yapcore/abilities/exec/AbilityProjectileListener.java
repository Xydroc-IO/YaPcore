package com.yapcore.abilities.exec;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.EntityRemoveEvent;

/**
 * Ensures spell ItemDisplay bodies are removed when the vanilla projectile dies
 * (block hit, void, etc.) — not only on living-entity hits.
 */
public final class AbilityProjectileListener implements Listener {

    private final ProjectileTracker tracker;

    public AbilityProjectileListener(ProjectileTracker tracker) {
        this.tracker = tracker;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        if (tracker.isTracked(projectile)) {
            tracker.onProjectileGone(projectile);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRemove(EntityRemoveEvent event) {
        Entity entity = event.getEntity();
        if (tracker.isTracked(entity)) {
            tracker.onProjectileGone(entity);
        }
    }
}
