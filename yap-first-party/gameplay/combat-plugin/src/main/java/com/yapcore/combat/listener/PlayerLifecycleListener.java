package com.yapcore.combat.listener;

import com.yapcore.combat.CombatConfig;
import com.yapcore.combat.service.CombatServiceImpl;
import com.yapcore.sched.YapSched;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class PlayerLifecycleListener implements Listener {

    private final JavaPlugin plugin;
    private final CombatConfig config;
    private final CombatServiceImpl combat;

    public PlayerLifecycleListener(JavaPlugin plugin, CombatConfig config, CombatServiceImpl combat) {
        this.plugin = plugin;
        this.config = config;
        this.combat = combat;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!config.enabled()) {
            return;
        }
        YapSched.entityLater(plugin, event.getPlayer(), () -> {
            combat.warmSkillCache(event.getPlayer().getUniqueId());
            combat.recalculate(event.getPlayer());
        }, 5L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        combat.unload(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        if (!config.enabled()) {
            return;
        }
        event.setKeepInventory(config.keepInventory());
        event.setKeepLevel(true);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (!config.enabled() || !config.restoreHpOnRespawn()) {
            return;
        }
        YapSched.entityLater(plugin, event.getPlayer(), () -> combat.restoreFull(event.getPlayer()), 1L);
    }
}
