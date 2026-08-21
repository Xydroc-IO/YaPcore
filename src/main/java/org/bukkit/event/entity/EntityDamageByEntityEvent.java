package org.bukkit.event.entity;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Paper/Bukkit event stub (YaPcore). */
public class EntityDamageByEntityEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public EntityDamageByEntityEvent() { super(false); }
    public EntityDamageByEntityEvent(Object... ctx) { super(false); }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
