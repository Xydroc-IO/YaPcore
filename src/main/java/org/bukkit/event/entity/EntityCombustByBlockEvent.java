package org.bukkit.event.entity;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Paper/Bukkit event stub (YaPcore). */
public class EntityCombustByBlockEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public EntityCombustByBlockEvent() { super(false); }
    public EntityCombustByBlockEvent(Object... ctx) { super(false); }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
