package org.bukkit.event.command;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Paper/Bukkit event stub (YaPcore). */
public class UnknownCommandEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public UnknownCommandEvent() { super(false); }
    public UnknownCommandEvent(Object... ctx) { super(false); }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
