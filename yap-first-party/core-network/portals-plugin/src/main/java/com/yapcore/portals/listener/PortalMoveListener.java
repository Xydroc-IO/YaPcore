package com.yapcore.portals.listener;

import com.yapcore.portals.Portal;
import com.yapcore.portals.PortalsConfig;
import com.yapcore.portals.service.PortalServiceImpl;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Optional;

/**
 * Region-thread: fire transfer when walking into or against a portal volume.
 * Fill is colored stained glass (pack = animated portal textures); adjacency counts.
 */
public final class PortalMoveListener implements Listener {

    private final PortalsConfig config;
    private final PortalServiceImpl portals;

    public PortalMoveListener(PortalsConfig config, PortalServiceImpl portals) {
        this.config = config;
        this.portals = portals;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!config.enabled()) {
            return;
        }
        Location to = event.getTo();
        if (to == null || to.getWorld() == null) {
            return;
        }
        Location from = event.getFrom();
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()
                && from.getWorld() != null
                && from.getWorld().equals(to.getWorld())) {
            return;
        }
        Player player = event.getPlayer();
        Optional<Portal> atTo = portals.at(to);
        String toName = atTo.map(Portal::name).orElse(null);
        String prev = portals.insideTracker().get(player.getUniqueId());
        if (toName == null) {
            if (prev != null) {
                portals.insideTracker().remove(player.getUniqueId());
            }
            return;
        }
        if (toName.equals(prev)) {
            return;
        }
        portals.insideTracker().put(player.getUniqueId(), toName);
        // Join / soft-switch often restores next to a pad; require leave+re-enter.
        if (portals.inJoinGrace(player.getUniqueId())) {
            return;
        }
        Portal portal = atTo.get();
        portals.transfer(player, portal);
    }
}
