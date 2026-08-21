package org.bukkit.event.entity;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Paper/Bukkit event stub (YaPcore). */
public class EntityBreakDoorEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public EntityBreakDoorEvent() { super(false); }
    public EntityBreakDoorEvent(Object... ctx) { super(false); }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
