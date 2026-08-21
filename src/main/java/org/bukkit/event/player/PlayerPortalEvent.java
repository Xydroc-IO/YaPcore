package org.bukkit.event.player;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Paper/Bukkit event stub (YaPcore). */
public class PlayerPortalEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public PlayerPortalEvent() { super(false); }
    public PlayerPortalEvent(Object... ctx) { super(false); }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
