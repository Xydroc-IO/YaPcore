package org.bukkit.event.inventory;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Paper/Bukkit event stub for YaPcore plugin compatibility (generated).
 */
public class HopperInventorySearchEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public HopperInventorySearchEvent() {
        super(false);
    }

    public HopperInventorySearchEvent(Object... ctx) {
        super(false);
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
