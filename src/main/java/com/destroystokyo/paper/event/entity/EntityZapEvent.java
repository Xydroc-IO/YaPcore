package com.destroystokyo.paper.event.entity;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Cancellable;

/** Paper/Bukkit event stub (YaPcore). */
public class EntityZapEvent extends Event implements org.bukkit.event.Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled;

    public EntityZapEvent() { super(false); }
    public EntityZapEvent(Object... ctx) { super(false); }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
