package com.yapcore.playerdata.gui;

import com.yapcore.playerdata.npc.NpcTraderService;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

public final class MenuListener implements Listener {

    private final Menus menus;
    private final NpcTraderService traders;

    public MenuListener(Menus menus, NpcTraderService traders) {
        this.menus = menus;
        this.traders = traders;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof YapMenuHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        if (holder.kind() == YapMenuHolder.Kind.NPC_TRADER) {
            Long traderId = holder.context();
            ItemStack clicked = event.getCurrentItem();
            String name = "";
            if (clicked != null && clicked.getItemMeta() != null && clicked.getItemMeta().displayName() != null) {
                name = PlainTextComponentSerializer.plainText().serialize(clicked.getItemMeta().displayName());
            }
            if (traderId != null) {
                traders.handleTradeClick(player, traderId, event.getSlot(), name);
            }
            return;
        }
        menus.handleClick(player, holder, event.getSlot(), event.isShiftClick());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof YapMenuHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player
                && event.getInventory().getHolder() instanceof YapMenuHolder holder) {
            menus.clearMeta(player);
            if (holder.kind() == YapMenuHolder.Kind.NPC_TRADER) {
                traders.clearClicks(player);
            }
        }
    }
}
