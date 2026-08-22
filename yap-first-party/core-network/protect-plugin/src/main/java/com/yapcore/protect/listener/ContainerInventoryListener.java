package com.yapcore.protect.listener;

import com.yapcore.protect.ProtectConfig;
import com.yapcore.protect.model.ChangeType;
import com.yapcore.protect.service.ProtectServiceImpl;
import com.yapcore.protect.util.InventoryCodec;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Logs container item changes on close (before/after inventory snapshots). */
public final class ContainerInventoryListener implements Listener {

    private final ProtectServiceImpl service;
    private final Map<String, String> openSnapshots = new ConcurrentHashMap<>();

    public ContainerInventoryListener(ProtectServiceImpl service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (!service.isLogging() || !service.config().logContainerInventory()) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        BlockState state = blockHolder(event.getInventory());
        if (state == null) {
            return;
        }
        openSnapshots.put(snapshotKey(player.getUniqueId(), state), InventoryCodec.encode(event.getInventory()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClose(InventoryCloseEvent event) {
        if (!service.isLogging() || !service.config().logContainerInventory()) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        BlockState state = blockHolder(event.getInventory());
        if (state == null) {
            return;
        }
        String key = snapshotKey(player.getUniqueId(), state);
        String before = openSnapshots.remove(key);
        if (before == null) {
            before = "";
        }
        String after = InventoryCodec.encode(event.getInventory());
        if (InventoryCodec.equalsEncoded(before, after)) {
            return;
        }
        service.logAsync(
                ChangeType.CONTAINER_INVENTORY,
                player.getUniqueId(),
                player.getName(),
                state.getWorld().getName(),
                state.getX(),
                state.getY(),
                state.getZ(),
                before,
                after);
    }

    private static BlockState blockHolder(Inventory inventory) {
        InventoryHolder holder = inventory.getHolder();
        if (holder instanceof BlockState state) {
            return state;
        }
        return null;
    }

    private static String snapshotKey(UUID playerId, BlockState state) {
        return state.getWorld().getName() + ':' + state.getX() + ':' + state.getY() + ':'
                + state.getZ() + ':' + playerId;
    }
}
