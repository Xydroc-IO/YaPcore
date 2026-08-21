package io.papermc.paper.event.player;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Paper/Bukkit event stub (YaPcore). */
public class PlayerStopUsingItemEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public PlayerStopUsingItemEvent() { super(false); }
    public PlayerStopUsingItemEvent(Object... ctx) { super(false); }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
