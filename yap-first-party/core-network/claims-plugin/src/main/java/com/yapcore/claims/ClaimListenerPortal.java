package com.yapcore.claims;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

final class ClaimListenerPortal implements Listener {

    private final ClaimService claims;

    ClaimListenerPortal(ClaimService claims) {
        this.claims = claims;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onNetherPortal(PlayerPortalEvent event) {
        if (!blockClaimNetherPortal(event.getPlayer(), event.getFrom(), event.getCause())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onNetherEntityPortal(EntityPortalEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        PlayerTeleportEvent.TeleportCause cause;
        if (event.getPortalType() == org.bukkit.PortalType.NETHER) {
            cause = PlayerTeleportEvent.TeleportCause.NETHER_PORTAL;
        } else if (event.getPortalType() == org.bukkit.PortalType.ENDER) {
            cause = PlayerTeleportEvent.TeleportCause.END_PORTAL;
        } else if (event.getPortalType() == org.bukkit.PortalType.END_GATEWAY) {
            cause = PlayerTeleportEvent.TeleportCause.END_GATEWAY;
        } else {
            cause = PlayerTeleportEvent.TeleportCause.UNKNOWN;
        }
        if (!blockClaimNetherPortal(player, event.getFrom(), cause)) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onNetherTeleport(PlayerTeleportEvent event) {
        if (event instanceof PlayerPortalEvent) {
            return;
        }
        if (!blockClaimNetherPortal(event.getPlayer(), event.getFrom(), event.getCause())) {
            return;
        }
        event.setCancelled(true);
    }

    private boolean blockClaimNetherPortal(
            Player player, org.bukkit.Location from, PlayerTeleportEvent.TeleportCause cause) {
        if (cause != PlayerTeleportEvent.TeleportCause.NETHER_PORTAL
                && cause != PlayerTeleportEvent.TeleportCause.END_PORTAL
                && cause != PlayerTeleportEvent.TeleportCause.END_GATEWAY) {
            return false;
        }
        org.bukkit.Location probe = from != null ? from : player.getLocation();
        if (claims.canUseNetherPortal(player, probe)) {
            return false;
        }
        String kind = cause == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL ? "nether" : "end";
        player.sendMessage("§cClaimed " + kind + " portal — only the owner and trusted players can use it.");
        return true;
    }
}
