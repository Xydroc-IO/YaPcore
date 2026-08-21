package org.bukkit.event.raid;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Paper/Bukkit event stub (YaPcore). */
public class RaidEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public RaidEvent() { super(false); }
    public RaidEvent(Object... ctx) { super(false); }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
