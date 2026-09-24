package com.yapcore.yap420.plant;

import com.yapcore.yap420.Yap420Plugin;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Keep tilled farmland from reverting to dirt.
 * <p>
 * YaP420 plants are ItemDisplays above air, so vanilla treats plots as empty farmland
 * and random-ticks / trampling turn them back to dirt.
 */
public final class FarmlandProtectListener implements Listener {

    private final Yap420Plugin plugin;

    public FarmlandProtectListener(Yap420Plugin plugin) {
        this.plugin = plugin;
    }

    private boolean enabled() {
        return plugin.yapConfig().protectFarmland();
    }

    /** Dry empty farmland → dirt (random tick). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) {
        if (!enabled()) {
            return;
        }
        if (event.getBlock().getType() == Material.FARMLAND) {
            event.setCancelled(true);
        }
    }

    /** Player jump / walk trample. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTrample(PlayerInteractEvent event) {
        if (!enabled() || event.getAction() != Action.PHYSICAL) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block != null && block.getType() == Material.FARMLAND) {
            event.setCancelled(true);
        }
    }

    /** Mob / falling-entity farmland change. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChange(EntityChangeBlockEvent event) {
        if (!enabled()) {
            return;
        }
        if (event.getBlock().getType() == Material.FARMLAND) {
            event.setCancelled(true);
        }
    }
}
