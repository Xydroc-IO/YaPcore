package com.yapcore.qol.listener;

import com.yapcore.qol.BlockBreakHelper;
import com.yapcore.qol.QolConfig;
import com.yapcore.qol.QolItems;
import com.yapcore.qol.mine.AreaMiner;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Area excavator: break one block → flat NxN plane (size baked into the item by staff). */
public final class ExcavatorListener implements Listener {

    private final QolConfig config;
    private final QolItems items;
    private final BlockBreakHelper breaks;

    public ExcavatorListener(QolConfig config, QolItems items, BlockBreakHelper breaks) {
        this.config = config;
        this.items = items;
        this.breaks = breaks;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!config.enabled() || !config.excavatorEnabled()) {
            return;
        }
        Player player = event.getPlayer();
        if (breaks.isBusy(player.getUniqueId())) {
            return;
        }
        if (!player.hasPermission("yapqol.excavator")) {
            return;
        }
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!items.isExcavator(tool)) {
            return;
        }
        Block origin = event.getBlock();
        if (origin.getType().isAir() || origin.getType().getHardness() < 0) {
            return;
        }
        if (!breaks.tryBegin(player.getUniqueId())) {
            return;
        }
        try {
            int size = items.mineSize(tool);
            int budget = Math.max(0, config.maxBlocksPerSwing() - 1);
            List<Block> plane = AreaMiner.planeAround(origin, player, size, budget);
            breaks.breakExtras(player, tool, plane, config.requireCorrectTool());
        } catch (RuntimeException e) {
            breaks.end(player.getUniqueId());
            throw e;
        }
    }
}
