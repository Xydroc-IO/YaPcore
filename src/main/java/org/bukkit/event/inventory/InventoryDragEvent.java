package org.bukkit.event.inventory;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class InventoryDragEvent extends PlayerEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Inventory inventory;
    private final Set<Integer> rawSlots;
    private final ItemStack oldCursor;
    private boolean cancelled;

    public InventoryDragEvent(Player player, Inventory inventory, Set<Integer> rawSlots, ItemStack oldCursor) {
        super(player);
        this.inventory = inventory;
        this.rawSlots = new HashSet<>(rawSlots != null ? rawSlots : Set.of());
        this.oldCursor = oldCursor;
    }

    public Inventory getInventory() {
        return inventory;
    }

    public Set<Integer> getRawSlots() {
        return Collections.unmodifiableSet(rawSlots);
    }

    public ItemStack getOldCursor() {
        return oldCursor;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
