package org.bukkit.event.server;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Paper/Bukkit event stub (YaPcore). */
public class PluginDisableEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public PluginDisableEvent() { super(false); }
    public PluginDisableEvent(Object... ctx) { super(false); }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
