package org.bukkit.event.world;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Paper/Bukkit event stub (YaPcore). */
public class WorldLoadEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public WorldLoadEvent() { super(false); }
    public WorldLoadEvent(Object... ctx) { super(false); }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
