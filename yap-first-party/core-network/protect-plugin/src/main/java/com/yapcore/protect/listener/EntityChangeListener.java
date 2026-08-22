package com.yapcore.protect.listener;

import com.yapcore.protect.model.ChangeType;
import com.yapcore.protect.service.ProtectServiceImpl;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

public final class EntityChangeListener implements Listener {

    private final ProtectServiceImpl service;

    public EntityChangeListener(ProtectServiceImpl service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        if (!service.isLogging() || !service.config().logEntityChange()) {
            return;
        }
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        LivingEntity entity = event.getEntity();
        service.logAsync(
                ChangeType.ENTITY_KILL,
                killer.getUniqueId(),
                killer.getName(),
                entity.getWorld().getName(),
                entity.getLocation().getBlockX(),
                entity.getLocation().getBlockY(),
                entity.getLocation().getBlockZ(),
                entity.getType().name(),
                "DEAD");
    }
}
