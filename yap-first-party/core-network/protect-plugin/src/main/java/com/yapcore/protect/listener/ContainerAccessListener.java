package com.yapcore.protect.listener;

import com.yapcore.protect.model.ChangeType;
import com.yapcore.protect.service.ProtectServiceImpl;
import com.yapcore.protect.util.BlockCodec;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.InventoryHolder;

public final class ContainerAccessListener implements Listener {

    private final ProtectServiceImpl service;

    public ContainerAccessListener(ProtectServiceImpl service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (!service.isLogging() || !service.config().logContainerAccess()) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof BlockState state)) {
            return;
        }
        service.logAsync(
                ChangeType.CONTAINER_ACCESS,
                player.getUniqueId(),
                player.getName(),
                state.getWorld().getName(),
                state.getX(),
                state.getY(),
                state.getZ(),
                BlockCodec.encode(state),
                "OPEN");
    }
}
