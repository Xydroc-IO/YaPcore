package com.yapcore.mechanics.listener;

import com.yapcore.mechanics.water.WaterWaves;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityToggleSwimEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WaterWavesListener implements Listener {

    private final WaterWaves waves;
    private final Map<UUID, Boolean> wasInWater = new ConcurrentHashMap<>();

    public WaterWavesListener(WaterWaves waves) {
        this.waves = waves;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) {
            return;
        }
        // Only when changing blocks — cheap splash detection
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        Player player = event.getPlayer();
        boolean now = player.isInWater() || player.isSwimming();
        Boolean was = wasInWater.put(player.getUniqueId(), now);
        if (was != null && !was && now) {
            waves.splash(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwim(EntityToggleSwimEvent event) {
        if (event.getEntity() instanceof Player player && event.isSwimming()) {
            Boolean was = wasInWater.put(player.getUniqueId(), true);
            if (was == null || !was) {
                waves.splash(player);
            }
        }
    }
}
