package org.bukkit.event.entity;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Paper/Bukkit event stub (YaPcore). */
public class EntityDamageByBlockEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public EntityDamageByBlockEvent() { super(false); }
    public EntityDamageByBlockEvent(Object... ctx) { super(false); }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
