package org.bukkit.event.inventory;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class InventoryClickEvent extends PlayerEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Inventory inventory;
    private final int slot;
    private final ItemStack currentItem;
    private boolean cancelled;

    public InventoryClickEvent(Player player, Inventory inventory, int slot, ItemStack currentItem) {
        super(player);
        this.inventory = inventory;
        this.slot = slot;
        this.currentItem = currentItem;
    }

    public Inventory getInventory() { return inventory; }
    public int getSlot() { return slot; }
    public ItemStack getCurrentItem() { return currentItem; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
