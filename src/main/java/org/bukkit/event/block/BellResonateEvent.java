package org.bukkit.event.block;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Paper/Bukkit event stub (YaPcore). */
public class BellResonateEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public BellResonateEvent() { super(false); }
    public BellResonateEvent(Object... ctx) { super(false); }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
