package io.papermc.paper.event.block;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Paper/Bukkit event stub (YaPcore). */
public class BellRingEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public BellRingEvent() { super(false); }
    public BellRingEvent(Object... ctx) { super(false); }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
