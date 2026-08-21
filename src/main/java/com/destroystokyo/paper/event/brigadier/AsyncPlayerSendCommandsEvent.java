package com.destroystokyo.paper.event.brigadier;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Paper/Bukkit event stub (YaPcore). */
public class AsyncPlayerSendCommandsEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public AsyncPlayerSendCommandsEvent() { super(true); }
    public AsyncPlayerSendCommandsEvent(Object... ctx) { super(true); }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
