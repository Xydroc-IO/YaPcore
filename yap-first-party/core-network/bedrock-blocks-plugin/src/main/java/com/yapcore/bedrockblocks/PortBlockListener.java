package com.yapcore.bedrockblocks;

import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

/** Place / break Bedrock catalog ports from tagged items and world interactions. */
public final class PortBlockListener implements Listener {

    private final PortBlockService service;

    public PortBlockListener(PortBlockService service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        ItemStack stack = event.getItemInHand();
        Optional<String> portId = service.portIdOfItem(stack);
        if (portId.isEmpty()) {
            return;
        }
        Optional<PortBlockDefinition> def = service.resolve(portId.get());
        if (def.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        Block target = event.getBlockPlaced();
        BlockFace face = event.getBlockAgainst().getFace(target);
        if (face == null) {
            face = BlockFace.NORTH;
        }
        // Region-safe place; consume item unless creative
        service.place(target, def.get().jePortId(), face);
        Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.CREATIVE) {
            stack.setAmount(stack.getAmount() - 1);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Optional<PortBlockDefinition> at = service.resolveAt(block);
        if (at.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        event.setDropItems(false);
        boolean drop = event.getPlayer().getGameMode() != GameMode.CREATIVE;
        service.breakPort(block, drop);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFrameBreak(HangingBreakByEntityEvent event) {
        if (!(event.getEntity() instanceof ItemFrame frame)) {
            return;
        }
        Optional<String> portId = service.readPortId(frame.getPersistentDataContainer());
        if (portId.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        boolean drop = true;
        if (event.getRemover() instanceof Player player) {
            drop = player.getGameMode() != GameMode.CREATIVE;
        }
        service.breakPort(frame.getLocation().getBlock(), drop);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDisplayHit(EntityDamageByEntityEvent event) {
        Entity ent = event.getEntity();
        if (!(ent instanceof ItemDisplay display)) {
            return;
        }
        Optional<String> portId = service.readPortId(display.getPersistentDataContainer());
        if (portId.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        boolean drop = player.getGameMode() != GameMode.CREATIVE;
        service.breakPort(display.getLocation().getBlock(), drop);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onFrameInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof ItemFrame frame)) {
            return;
        }
        if (service.readPortId(frame.getPersistentDataContainer()).isPresent()) {
            // Keep port frames fixed / non-rotatable by players
            event.setCancelled(true);
        }
    }
}
