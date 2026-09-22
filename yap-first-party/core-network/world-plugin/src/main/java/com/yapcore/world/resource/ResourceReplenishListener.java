package com.yapcore.world.resource;

import com.yapcore.claims.ClaimLookups;
import com.yapcore.sched.YapSched;
import com.yapcore.world.WorldConfig;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Replaces broken ores/trees in wilderness after a delay — skips player claims.
 */
public final class ResourceReplenishListener implements Listener {

    private final JavaPlugin plugin;
    private volatile WorldConfig config;

    public ResourceReplenishListener(JavaPlugin plugin, WorldConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void setConfig(WorldConfig config) {
        this.config = config;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        WorldConfig cfg = config;
        if (cfg == null || !cfg.replenishEnabled()) {
            return;
        }
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        Block block = event.getBlock();
        Material type = block.getType();
        if (!cfg.isReplenishMaterial(type)) {
            return;
        }
        if (!cfg.replenishAppliesTo(block.getWorld().getName())) {
            return;
        }
        Location loc = block.getLocation();
        if (!ClaimLookups.canSystemModify(loc)) {
            return;
        }
        Material place = type;
        int delayTicks = cfg.replenishDelaySeconds() * 20;
        int chunkX = loc.getBlockX() >> 4;
        int chunkZ = loc.getBlockZ() >> 4;
        YapSched.regionChunkLater(plugin, loc.getWorld(), chunkX, chunkZ, () -> {
            if (!ClaimLookups.canSystemModify(loc)) {
                return;
            }
            Block now = loc.getBlock();
            if (!now.getType().isAir() && now.getType() != Material.WATER && now.getType() != Material.LAVA) {
                return;
            }
            now.setType(place, false);
        }, delayTicks);
    }
}
