package org.bukkit.event.world;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Paper/Bukkit event stub (YaPcore). */
public class AsyncStructureGenerateEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public AsyncStructureGenerateEvent() { super(true); }
    public AsyncStructureGenerateEvent(Object... ctx) { super(true); }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
