package io.papermc.paper.event.block;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Paper/Bukkit event stub (YaPcore). */
public class BeaconDeactivatedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public BeaconDeactivatedEvent() { super(false); }
    public BeaconDeactivatedEvent(Object... ctx) { super(false); }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
