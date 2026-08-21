package org.bukkit.conversations;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Paper/Bukkit event stub (YaPcore). */
public class ConversationAbandonedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public ConversationAbandonedEvent() { super(false); }
    public ConversationAbandonedEvent(Object... ctx) { super(false); }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
