package org.bukkit;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Paper/Bukkit event stub (YaPcore). */
public class GameEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public GameEvent() { super(false); }
    public GameEvent(Object... ctx) { super(false); }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
