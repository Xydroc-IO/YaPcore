package io.papermc.paper.event.block;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Paper/Bukkit event stub (YaPcore). */
public class BlockLockCheckEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public BlockLockCheckEvent() { super(false); }
    public BlockLockCheckEvent(Object... ctx) { super(false); }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
