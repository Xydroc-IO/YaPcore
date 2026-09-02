package com.yapcore.playerdata.bag;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class BackpackListener implements Listener {

    private final BackpackService backpack;

    public BackpackListener(BackpackService backpack) {
        this.backpack = backpack;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        BackpackHolder holder = BackpackInventories.bag(top);
        if (holder == null) {
            return;
        }
        if (BackpackService.isNav(event.getCursor()) || BackpackService.isNav(event.getCurrentItem())) {
            event.setCancelled(true);
        }
        if (event.getClickedInventory() == null || event.getClickedInventory() != top) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot < BackpackService.STORAGE_SLOTS) {
            if (BackpackService.isNav(event.getCurrentItem())) {
                event.setCancelled(true);
                event.setCurrentItem(null);
            }
            return;
        }
        event.setCancelled(true);
        int target = BackpackService.navPage(event.getCurrentItem());
        if (target > 0) {
            backpack.switchPage(player, holder, target);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (BackpackInventories.bag(top) == null) {
            return;
        }
        if (BackpackService.isNav(event.getOldCursor()) || BackpackService.isNav(event.getCursor())) {
            event.setCancelled(true);
            return;
        }
        for (int slot : event.getRawSlots()) {
            if (slot >= BackpackService.STORAGE_SLOTS && slot < BackpackService.GUI_SIZE) {
                event.setCancelled(true);
                return;
            }
            ItemStack existing = top.getItem(slot);
            if (slot < BackpackService.STORAGE_SLOTS && BackpackService.isNav(existing)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        BackpackHolder holder = BackpackInventories.bag(event.getInventory());
        if (holder == null) {
            return;
        }
        backpack.saveFrom(event.getInventory(), holder);
        if (!backpack.isSwitching(player.getUniqueId())) {
            backpack.cancelPending(player.getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        backpack.cancelPending(event.getPlayer().getUniqueId());
    }
}
