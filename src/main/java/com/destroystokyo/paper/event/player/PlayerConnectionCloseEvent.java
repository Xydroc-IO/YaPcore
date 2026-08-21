package com.destroystokyo.paper.event.player;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Paper/Bukkit event stub (YaPcore). */
public class PlayerConnectionCloseEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public PlayerConnectionCloseEvent() { super(false); }
    public PlayerConnectionCloseEvent(Object... ctx) { super(false); }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
