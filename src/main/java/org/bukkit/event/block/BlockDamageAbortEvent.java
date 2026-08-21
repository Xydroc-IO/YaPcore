package org.bukkit.event.block;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Paper/Bukkit event stub (YaPcore). */
public class BlockDamageAbortEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public BlockDamageAbortEvent() { super(false); }
    public BlockDamageAbortEvent(Object... ctx) { super(false); }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
