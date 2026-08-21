package org.bukkit.event.inventory;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Paper/Bukkit event stub (YaPcore). */
public class FurnaceExtractEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public FurnaceExtractEvent() { super(false); }
    public FurnaceExtractEvent(Object... ctx) { super(false); }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
