package org.bukkit.event.block;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Paper/Bukkit event stub (YaPcore). */
public class BlockFormEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public BlockFormEvent() { super(false); }
    public BlockFormEvent(Object... ctx) { super(false); }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
