package com.yapcore.portals.listener;

import com.yapcore.portals.service.PortalServiceImpl;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PortalQuitListener implements Listener {

    private final PortalServiceImpl portals;

    public PortalQuitListener(PortalServiceImpl portals) {
        this.portals = portals;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        portals.clearPlayerState(event.getPlayer().getUniqueId());
    }
}
