package org.bukkit.event.world;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Paper/Bukkit event stub (YaPcore). */
public class EntitiesLoadEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public EntitiesLoadEvent() { super(false); }
    public EntitiesLoadEvent(Object... ctx) { super(false); }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
