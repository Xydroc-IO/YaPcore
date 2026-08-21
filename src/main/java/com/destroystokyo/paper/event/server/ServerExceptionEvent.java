package com.destroystokyo.paper.event.server;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Paper/Bukkit event stub (YaPcore). */
public class ServerExceptionEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public ServerExceptionEvent() { super(false); }
    public ServerExceptionEvent(Object... ctx) { super(false); }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
