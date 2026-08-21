package io.papermc.paper.event.player;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Paper/Bukkit event stub (YaPcore). */
public class AsyncChatCommandDecorateEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public AsyncChatCommandDecorateEvent() { super(true); }
    public AsyncChatCommandDecorateEvent(Object... ctx) { super(true); }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
