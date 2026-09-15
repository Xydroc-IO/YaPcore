package com.yapcore.qol.listener;

import com.yapcore.qol.BlockBreakHelper;
import com.yapcore.qol.QolConfig;
import com.yapcore.qol.QolItems;
import com.yapcore.qol.timber.TreeFeller;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Timber axe: one trunk break fells connected logs (and optional leaves). */
public final class TimberListener implements Listener {

    private final QolConfig config;
    private final QolItems items;
    private final BlockBreakHelper breaks;

    public TimberListener(QolConfig config, QolItems items, BlockBreakHelper breaks) {
        this.config = config;
        this.items = items;
        this.breaks = breaks;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!config.enabled() || !config.timberEnabled()) {
            return;
        }
        Player player = event.getPlayer();
        if (breaks.isBusy(player.getUniqueId())) {
            return;
        }
        if (!player.hasPermission("yapqol.timber")) {
            return;
        }
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!items.isTimberAxe(tool)) {
            return;
        }
        Block origin = event.getBlock();
        if (!TreeFeller.isLog(origin.getType())) {
            return;
        }
        if (!breaks.tryBegin(player.getUniqueId())) {
            return;
        }
        try {
            String family = TreeFeller.woodFamily(origin.getType());
            List<Block> logs = TreeFeller.collectLogs(origin, config.maxLogs());
            List<Block> all = new ArrayList<>(logs);
            if (config.breakLeaves() && config.maxLeaves() > 0) {
                List<Block> seed = new ArrayList<>(logs);
                seed.add(origin);
                all.addAll(TreeFeller.collectLeaves(seed, family, config.maxLeaves()));
            }
            int planned = breaks.breakExtras(player, tool, all, false);
            if (planned > 0 && !logs.isEmpty()) {
                player.sendMessage(QolConfig.legacy().deserialize(
                        config.msgTimberFell().replace("{count}", String.valueOf(logs.size()))));
            }
        } catch (RuntimeException e) {
            breaks.end(player.getUniqueId());
            throw e;
        }
    }
}
