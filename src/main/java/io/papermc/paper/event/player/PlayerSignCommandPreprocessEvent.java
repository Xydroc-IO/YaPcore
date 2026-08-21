package io.papermc.paper.event.player;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Paper/Bukkit event stub (YaPcore). */
public class PlayerSignCommandPreprocessEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public PlayerSignCommandPreprocessEvent() { super(false); }
    public PlayerSignCommandPreprocessEvent(Object... ctx) { super(false); }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
