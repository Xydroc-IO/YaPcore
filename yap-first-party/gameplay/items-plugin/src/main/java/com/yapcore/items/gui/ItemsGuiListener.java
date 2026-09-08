package com.yapcore.items.gui;

import com.yapcore.items.ItemsPlugin;
import com.yapcore.items.item.ItemFactory;
import com.yapcore.sched.YapSched;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public final class ItemsGuiListener implements Listener {

    private final ItemsPlugin plugin;

    public ItemsGuiListener(ItemsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ItemsGui.Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot == ItemsGui.SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (slot == ItemsGui.SLOT_PREV) {
            plugin.gui().open(player, holder.page() - 1);
            return;
        }
        if (slot == ItemsGui.SLOT_NEXT) {
            plugin.gui().open(player, holder.page() + 1);
            return;
        }
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) {
            return;
        }
        ItemFactory factory = plugin.factory();
        var idOpt = factory.idOf(clicked);
        if (idOpt.isEmpty()) {
            return;
        }
        int amount = event.isShiftClick() ? 64 : 1;
        factory.create(idOpt.get(), amount).ifPresent(stack ->
                YapSched.entity(plugin, player, () -> player.getInventory().addItem(stack)));
    }
}
