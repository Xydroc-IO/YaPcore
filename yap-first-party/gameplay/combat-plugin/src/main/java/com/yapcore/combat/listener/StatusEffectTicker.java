package com.yapcore.combat.listener;

import com.yapcore.combat.service.CombatServiceImpl;
import com.yapcore.combat.status.StatusEffectService;
import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public final class StatusEffectTicker implements Listener, Runnable {

    private final JavaPlugin plugin;
    private final StatusEffectService status;
    private final CombatServiceImpl combat;

    public StatusEffectTicker(JavaPlugin plugin, StatusEffectService status, CombatServiceImpl combat) {
        this.plugin = plugin;
        this.status = status;
        this.combat = combat;
    }

    public void start() {
        YapSched.globalTimer(plugin, this, 20L, 20L);
    }

    @Override
    public void run() {
        for (UUID id : status.activeEntityIds()) {
            org.bukkit.entity.Entity entity = Bukkit.getEntity(id);
            if (!(entity instanceof LivingEntity living) || !living.isValid()) {
                continue;
            }
            YapSched.entity(plugin, living, () -> status.tick(living, combat));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        status.clear(event.getEntity().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        status.clear(event.getEntity().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        status.clear(event.getPlayer().getUniqueId());
    }
}
