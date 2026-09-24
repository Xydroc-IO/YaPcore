package com.yapcore.playerdata.gui;

import com.yapcore.playerdata.npc.NpcTraderService;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
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
        if (holder.kind() == YapMenuHolder.Kind.NPC_TRADER
                || holder.kind() == YapMenuHolder.Kind.NPC_TRADER_QTY) {
            if (traders == null) {
                return;
            }
            ItemStack clicked = event.getCurrentItem();
            String name = "";
            if (clicked != null && clicked.getItemMeta() != null && clicked.getItemMeta().displayName() != null) {
                name = PlainTextComponentSerializer.plainText().serialize(clicked.getItemMeta().displayName());
            }
            Object ctx = holder.context();
            if (holder.kind() == YapMenuHolder.Kind.NPC_TRADER_QTY
                    && ctx instanceof NpcTraderService.QtyGuiCtx qty) {
                traders.handleQtyClick(player, qty, event.getSlot(), name);
                return;
            }
            long traderId;
            int page = 0;
            if (ctx instanceof NpcTraderService.TraderGuiCtx gui) {
                traderId = gui.traderId();
                page = gui.page();
            } else if (ctx instanceof Long id) {
                traderId = id;
            } else {
                return;
            }
            traders.handleTradeClick(player, traderId, page, event.getSlot(), name, event.getClick());
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
            if (holder.kind() == YapMenuHolder.Kind.NPC_TRADER
                    || holder.kind() == YapMenuHolder.Kind.NPC_TRADER_QTY) {
                traders.clearClicks(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onComposeChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        String raw = PlainTextComponentSerializer.plainText().serialize(event.message());
        if (menus.mailMenus.handleChat(player, raw) || menus.auctionMenus.handleChat(player, raw)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        menus.mailMenus.clearPending(event.getPlayer());
        menus.auctionMenus.clearPending(event.getPlayer());
    }
}
