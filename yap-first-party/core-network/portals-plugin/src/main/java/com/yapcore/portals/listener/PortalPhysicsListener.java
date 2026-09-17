package com.yapcore.portals.listener;

import com.yapcore.portals.Portal;
import com.yapcore.portals.service.PortalServiceImpl;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Hub backends must not run vanilla nether/end portal teleports — YaP Link Connect owns
 * cross-server moves. Cancel dimension hops in managed volumes and any NETHER_PORTAL cause
 * on this Folia instance (lobby should not dump players into {@code world_nether}).
 */
public final class PortalPhysicsListener implements Listener {

    private final PortalServiceImpl portals;

    public PortalPhysicsListener(PortalServiceImpl portals) {
        this.portals = portals;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onPlayerPortal(PlayerPortalEvent event) {
        if (shouldBlockDimensionHop(event.getPlayer(), event.getFrom(), event.getCause())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onEntityPortal(EntityPortalEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Player player)) {
            return;
        }
        if (shouldBlockDimensionHop(player, event.getFrom(), PlayerTeleportEvent.TeleportCause.NETHER_PORTAL)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onTeleport(PlayerTeleportEvent event) {
        if (shouldBlockDimensionHop(event.getPlayer(), event.getFrom(), event.getCause())) {
            event.setCancelled(true);
        }
    }

    private boolean shouldBlockDimensionHop(
            Player player, Location from, PlayerTeleportEvent.TeleportCause cause) {
        if (cause != PlayerTeleportEvent.TeleportCause.NETHER_PORTAL
                && cause != PlayerTeleportEvent.TeleportCause.END_PORTAL
                && cause != PlayerTeleportEvent.TeleportCause.END_GATEWAY) {
            return false;
        }
        // Always block vanilla nether hops on this backend — fleet hubs use Link for survival.
        if (cause == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) {
            return true;
        }
        return touchesManagedPortal(player, from);
    }

    private boolean touchesManagedPortal(Player player, Location from) {
        if (player == null) {
            return false;
        }
        if (enabledAt(from) || enabledAt(player.getLocation())) {
            return true;
        }
        return enabledAt(player.getEyeLocation());
    }

    private boolean enabledAt(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return false;
        }
        return portals.at(loc).filter(Portal::enabled).isPresent();
    }
}
