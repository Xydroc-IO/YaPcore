package com.yapcore.yapblock.gen;

import com.yapcore.yapblock.IslandSnapshot;
import com.yapcore.yapblock.YapblockPlugin;
import com.yapcore.yapblock.service.IslandServiceImpl;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFormEvent;

/** Replaces cobble/stone formation on islands with tiered generator loot. */
public final class IslandGeneratorListener implements Listener {

    private final YapblockPlugin plugin;
    private final CobbleGenTables tables;

    public IslandGeneratorListener(YapblockPlugin plugin, CobbleGenTables tables) {
        this.plugin = plugin;
        this.tables = tables;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onForm(BlockFormEvent event) {
        IslandServiceImpl service = plugin.service();
        if (service == null || !service.enabled()) {
            return;
        }
        Block block = event.getBlock();
        Material result = event.getNewState().getType();
        if (result != Material.COBBLESTONE && result != Material.STONE) {
            return;
        }
        IslandSnapshot island = service.index().at(block.getLocation()).orElse(null);
        if (island == null) {
            return;
        }
        Material rolled = tables.roll(island.genTier());
        if (rolled == result) {
            return;
        }
        event.setCancelled(true);
        block.setType(rolled, false);
    }
}
