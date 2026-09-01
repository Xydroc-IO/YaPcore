package io.papermc.paper.event.world.border;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Paper/Bukkit event stub (YaPcore). */
public class WorldBorderEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public WorldBorderEvent() { super(false); }
    public WorldBorderEvent(Object... ctx) { super(false); }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
