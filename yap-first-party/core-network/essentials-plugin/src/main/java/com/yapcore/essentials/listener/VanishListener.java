package com.yapcore.essentials.listener;

import com.yapcore.essentials.store.VanishService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class VanishListener implements Listener {

    private final VanishService vanish;

    public VanishListener(VanishService vanish) {
        this.vanish = vanish;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (vanish.isVanished(event.getPlayer().getUniqueId())) {
            vanish.hide(event.getPlayer());
        }
        for (var online : event.getPlayer().getServer().getOnlinePlayers()) {
            if (vanish.isVanished(online.getUniqueId())
                    && !event.getPlayer().hasPermission("yapessentials.vanish")) {
                event.getPlayer().hidePlayer(vanish.plugin(), online);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (vanish.isVanished(event.getPlayer().getUniqueId())) {
            vanish.show(event.getPlayer());
        }
    }
}
