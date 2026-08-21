package com.destroystokyo.paper.event.inventory;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Paper/Bukkit event stub (YaPcore). */
public class PrepareResultEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public PrepareResultEvent() { super(false); }
    public PrepareResultEvent(Object... ctx) { super(false); }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
