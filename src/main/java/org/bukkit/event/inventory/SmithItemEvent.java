package org.bukkit.event.inventory;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Paper/Bukkit event stub (YaPcore). */
public class SmithItemEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public SmithItemEvent() { super(false); }
    public SmithItemEvent(Object... ctx) { super(false); }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
