package com.destroystokyo.paper.event.entity;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Paper/Bukkit event stub (YaPcore). */
public class EntityRemoveFromWorldEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public EntityRemoveFromWorldEvent() { super(false); }
    public EntityRemoveFromWorldEvent(Object... ctx) { super(false); }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
