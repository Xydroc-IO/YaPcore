package org.bukkit.event.block;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Paper/Bukkit event stub (YaPcore). */
public class BlockMultiPlaceEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public BlockMultiPlaceEvent() { super(false); }
    public BlockMultiPlaceEvent(Object... ctx) { super(false); }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
