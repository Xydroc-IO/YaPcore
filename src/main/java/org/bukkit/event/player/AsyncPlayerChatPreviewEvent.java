package org.bukkit.event.player;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Paper/Bukkit event stub (YaPcore). */
public class AsyncPlayerChatPreviewEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public AsyncPlayerChatPreviewEvent() { super(true); }
    public AsyncPlayerChatPreviewEvent(Object... ctx) { super(true); }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
