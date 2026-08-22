package com.yapcore.perms.listener;

import com.yapcore.perms.PermsPlugin;
import com.yapcore.sched.YapSched;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class JoinListener implements Listener {

    private final PermsPlugin plugin;

    public JoinListener(PermsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        YapSched.global(plugin, () -> plugin.refresh(event.getPlayer()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.applicator().detach(event.getPlayer());
    }
}
