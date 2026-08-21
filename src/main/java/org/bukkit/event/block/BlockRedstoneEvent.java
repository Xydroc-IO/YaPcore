package org.bukkit.event.block;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Paper/Bukkit event stub (YaPcore). */
public class BlockRedstoneEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public BlockRedstoneEvent() { super(false); }
    public BlockRedstoneEvent(Object... ctx) { super(false); }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
