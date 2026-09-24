package com.yapcore.yapblock.protect;

import com.yapcore.yapblock.YapblockPlugin;
import org.bukkit.event.Listener;

/** Facade that registers split island protection listeners. */
public final class IslandListener implements Listener {

    public IslandListener(YapblockPlugin plugin) {
        var pm = plugin.getServer().getPluginManager();
        pm.registerEvents(new IslandListenerBuild(plugin), plugin);
        pm.registerEvents(new IslandListenerCombat(plugin), plugin);
        pm.registerEvents(new IslandListenerExplosion(plugin), plugin);
        pm.registerEvents(new IslandListenerInteract(plugin), plugin);
        pm.registerEvents(new IslandListenerEntity(plugin), plugin);
        pm.registerEvents(new IslandListenerMove(plugin), plugin);
        pm.registerEvents(new IslandListenerRespawn(plugin), plugin);
    }
}
