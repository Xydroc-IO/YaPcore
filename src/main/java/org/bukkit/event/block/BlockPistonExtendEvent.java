package org.bukkit.event.block;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Paper/Bukkit event stub (YaPcore). */
public class BlockPistonExtendEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public BlockPistonExtendEvent() { super(false); }
    public BlockPistonExtendEvent(Object... ctx) { super(false); }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
