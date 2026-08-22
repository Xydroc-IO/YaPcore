package com.yapcore.factions.listener;

import com.yapcore.factions.service.FactionServiceImpl;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public final class FactionDeathListener implements Listener {

    private final FactionServiceImpl factions;

    public FactionDeathListener(FactionServiceImpl factions) {
        this.factions = factions;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        factions.applyDeathPowerLoss(event.getEntity().getUniqueId());
    }
}
