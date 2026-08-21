package io.papermc.paper.event.packet;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Paper/Bukkit event stub (YaPcore). */
public class PlayerChunkLoadEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public PlayerChunkLoadEvent() { super(false); }
    public PlayerChunkLoadEvent(Object... ctx) { super(false); }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
