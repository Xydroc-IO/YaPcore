package org.bukkit.event.vehicle;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Paper/Bukkit event stub (YaPcore). */
public class VehicleCollisionEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public VehicleCollisionEvent() { super(false); }
    public VehicleCollisionEvent(Object... ctx) { super(false); }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
