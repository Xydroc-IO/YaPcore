package org.bukkit.event.player;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Paper/Bukkit event stub (YaPcore). */
public class PlayerHideEntityEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public PlayerHideEntityEvent() { super(false); }
    public PlayerHideEntityEvent(Object... ctx) { super(false); }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
