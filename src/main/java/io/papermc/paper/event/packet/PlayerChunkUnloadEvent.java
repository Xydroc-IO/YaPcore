package io.papermc.paper.event.packet;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Paper/Bukkit event stub (YaPcore). */
public class PlayerChunkUnloadEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public PlayerChunkUnloadEvent() { super(false); }
    public PlayerChunkUnloadEvent(Object... ctx) { super(false); }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
