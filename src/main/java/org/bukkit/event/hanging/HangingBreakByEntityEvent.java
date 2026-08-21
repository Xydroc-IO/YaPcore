package org.bukkit.event.hanging;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Paper/Bukkit event stub (YaPcore). */
public class HangingBreakByEntityEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public HangingBreakByEntityEvent() { super(false); }
    public HangingBreakByEntityEvent(Object... ctx) { super(false); }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
