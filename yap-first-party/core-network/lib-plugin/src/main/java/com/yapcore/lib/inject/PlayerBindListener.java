package com.yapcore.lib.inject;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.event.server.PluginDisableEvent;

import com.yapcore.lib.dispatch.ListenerRegistry;

public final class PlayerBindListener implements Listener {

    private final ChannelInjector injector;
    private final ConnectionTracker tracker;
    private final ListenerRegistry registry;

    public PlayerBindListener(ChannelInjector injector, ConnectionTracker tracker, ListenerRegistry registry) {
        this.injector = injector;
        this.tracker = tracker;
        this.registry = registry;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        injector.injectPlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        tracker.unbind(event.getPlayer());
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        Plugin disabled = event.getPlugin();
        if (disabled != null) {
            registry.remove(disabled);
        }
    }
}
