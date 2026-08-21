package com.destroystokyo.paper.event.server;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Paper/Bukkit event stub (YaPcore). */
public class ServerTickStartEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public ServerTickStartEvent() { super(false); }
    public ServerTickStartEvent(Object... ctx) { super(false); }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
