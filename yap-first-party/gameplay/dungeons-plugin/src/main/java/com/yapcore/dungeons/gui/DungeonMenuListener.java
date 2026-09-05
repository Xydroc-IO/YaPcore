package com.yapcore.dungeons.gui;

import com.yapcore.dungeons.DungeonService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class DungeonMenuListener implements Listener {

    private final DungeonService service;
    private final DungeonMenu menu;

    public DungeonMenuListener(DungeonService service, DungeonMenu menu) {
        this.service = service;
        this.menu = menu;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof DungeonMenuHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot == 53) {
            player.closeInventory();
            return;
        }
        if (slot == 45) {
            menu.open(player, 0);
            return;
        }
        if (slot == 46) {
            menu.open(player, 1);
            return;
        }
        if (slot == 47) {
            menu.open(player, 2);
            return;
        }
        if (slot < 0 || slot >= 45) {
            return;
        }
        ItemStack stack = event.getCurrentItem();
        if (stack == null || !stack.hasItemMeta()) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null || !meta.hasCustomModelData()) {
            return;
        }
        int level = meta.getCustomModelData();
        player.closeInventory();
        player.sendMessage("§7Starting dungeon L" + level + "…");
        service.startRun(player, level);
    }
}
