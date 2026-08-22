package com.yapcore.protect.listener;

import com.yapcore.protect.model.ChangeType;
import com.yapcore.protect.service.ProtectServiceImpl;
import com.yapcore.protect.util.BlockCodec;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

public final class BlockChangeListener implements Listener {

    private final ProtectServiceImpl service;

    public BlockChangeListener(ProtectServiceImpl service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!service.isLogging() || !service.config().logBlockBreak()) {
            return;
        }
        Player player = event.getPlayer();
        service.logAsync(
                ChangeType.BLOCK_BREAK,
                player.getUniqueId(),
                player.getName(),
                event.getBlock().getWorld().getName(),
                event.getBlock().getX(),
                event.getBlock().getY(),
                event.getBlock().getZ(),
                BlockCodec.encode(event.getBlock()),
                "AIR");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!service.isLogging() || !service.config().logBlockPlace()) {
            return;
        }
        Player player = event.getPlayer();
        var placed = event.getBlockPlaced();
        var replaced = event.getBlockReplacedState();
        service.logAsync(
                ChangeType.BLOCK_PLACE,
                player.getUniqueId(),
                player.getName(),
                placed.getWorld().getName(),
                placed.getX(),
                placed.getY(),
                placed.getZ(),
                BlockCodec.encode(replaced),
                BlockCodec.encode(placed));
    }
}
