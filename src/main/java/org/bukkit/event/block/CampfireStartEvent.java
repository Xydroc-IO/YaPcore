package org.bukkit.event.block;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Paper/Bukkit event stub (YaPcore). */
public class CampfireStartEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public CampfireStartEvent() { super(false); }
    public CampfireStartEvent(Object... ctx) { super(false); }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
