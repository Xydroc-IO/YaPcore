package org.bukkit.event.world;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Paper/Bukkit event stub (YaPcore). */
public class WorldInitEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public WorldInitEvent() { super(false); }
    public WorldInitEvent(Object... ctx) { super(false); }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
