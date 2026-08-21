package org.bukkit.event.player;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Paper/Bukkit event stub (YaPcore). */
public class PlayerChangedWorldEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public PlayerChangedWorldEvent() { super(false); }
    public PlayerChangedWorldEvent(Object... ctx) { super(false); }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
