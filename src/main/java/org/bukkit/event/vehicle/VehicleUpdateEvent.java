package org.bukkit.event.vehicle;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Paper/Bukkit event stub (YaPcore). */
public class VehicleUpdateEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public VehicleUpdateEvent() { super(false); }
    public VehicleUpdateEvent(Object... ctx) { super(false); }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
