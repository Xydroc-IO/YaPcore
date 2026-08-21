package org.bukkit.event.hanging;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Paper/Bukkit event stub (YaPcore). */
public class HangingEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public HangingEvent() { super(false); }
    public HangingEvent(Object... ctx) { super(false); }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
