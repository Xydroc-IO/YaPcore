package com.yapcore.skills.gui;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class SkillsMenuListener implements Listener {

    private static InventoryHolder holderOf(Inventory inventory) {
        if (inventory == null) {
            return null;
        }
        InventoryHolder custom = inventory.getHolder(false);
        return custom != null ? custom : inventory.getHolder();
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(holderOf(event.getInventory()) instanceof SkillsMenuHolder)) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (holderOf(event.getInventory()) instanceof SkillsMenuHolder) {
            event.setCancelled(true);
        }
    }
}
