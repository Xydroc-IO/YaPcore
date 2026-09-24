package com.yapcore.claims;

import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

final class ClaimListenerMob implements Listener {

    private final ClaimService claims;

    ClaimListenerMob(ClaimService claims) {
        this.claims = claims;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHostileMove(io.papermc.paper.event.entity.EntityMoveEvent event) {
        if (!event.hasChangedBlock()) {
            return;
        }
        org.bukkit.entity.LivingEntity entity = event.getEntity();
        if (!(entity instanceof org.bukkit.entity.Enemy)) {
            return;
        }
        if (ClaimListenerEntities.intentionalSpawn(entity)) {
            return;
        }
        org.bukkit.Location to = event.getTo();
        if (to == null || claims.isMobEntryAllowed(to)) {
            return;
        }
        event.setCancelled(true);
        if (!claims.isMobEntryAllowed(event.getFrom())) {
            entity.remove();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHostileTeleport(org.bukkit.event.entity.EntityTeleportEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof org.bukkit.entity.Enemy)) {
            return;
        }
        if (ClaimListenerEntities.intentionalSpawn(entity)) {
            return;
        }
        org.bukkit.Location to = event.getTo();
        if (to == null || claims.isMobEntryAllowed(to)) {
            return;
        }
        event.setCancelled(true);
        if (!claims.isMobEntryAllowed(event.getFrom())) {
            entity.remove();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMobSpawn(CreatureSpawnEvent event) {
        if (ClaimListenerEntities.isIntentionalSpawnReason(event.getSpawnReason())) {
            return;
        }
        if (event.getEntity() instanceof org.bukkit.entity.Enemy && !claims.isMobEntryAllowed(event.getLocation())) {
            event.setCancelled(true);
            return;
        }
        if (!claims.isMobSpawningAllowed(event.getLocation())) {
            event.setCancelled(true);
        }
    }
}
