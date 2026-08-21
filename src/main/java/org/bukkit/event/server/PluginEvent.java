package org.bukkit.event.server;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Paper/Bukkit event stub (YaPcore). */
public class PluginEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public PluginEvent() { super(false); }
    public PluginEvent(Object... ctx) { super(false); }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
