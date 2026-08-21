package org.bukkit.event.vehicle;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Paper/Bukkit event stub (YaPcore). */
public class VehicleMoveEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public VehicleMoveEvent() { super(false); }
    public VehicleMoveEvent(Object... ctx) { super(false); }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
