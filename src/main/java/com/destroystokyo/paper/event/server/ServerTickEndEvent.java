package com.destroystokyo.paper.event.server;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Paper/Bukkit event stub (YaPcore). */
public class ServerTickEndEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public ServerTickEndEvent() { super(false); }
    public ServerTickEndEvent(Object... ctx) { super(false); }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
