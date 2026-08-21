package org.bukkit.event.entity;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Paper/Bukkit event stub (YaPcore). */
public class EntityTargetLivingEntityEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public EntityTargetLivingEntityEvent() { super(false); }
    public EntityTargetLivingEntityEvent(Object... ctx) { super(false); }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
