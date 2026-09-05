package com.yapcore.essentials.listener;

import com.yapcore.essentials.EssentialsPlugin;
import com.yapcore.essentials.water.WaterWaves;
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

    private final EssentialsPlugin plugin;
    private final Map<UUID, Boolean> wasInWater = new ConcurrentHashMap<>();

    public WaterWavesListener(EssentialsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) {
            return;
        }
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        Player player = event.getPlayer();
        boolean now = player.isInWater() || player.isSwimming();
        Boolean was = wasInWater.put(player.getUniqueId(), now);
        if (was != null && !was && now) {
            WaterWaves waves = plugin.waterWaves();
            if (waves != null) {
                waves.splash(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwim(EntityToggleSwimEvent event) {
        if (event.getEntity() instanceof Player player && event.isSwimming()) {
            Boolean was = wasInWater.put(player.getUniqueId(), true);
            if (was == null || !was) {
                WaterWaves waves = plugin.waterWaves();
                if (waves != null) {
                    waves.splash(player);
                }
            }
        }
    }
}
