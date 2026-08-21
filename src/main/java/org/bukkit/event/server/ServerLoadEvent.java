package org.bukkit.event.server;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Paper/Bukkit event stub for YaPcore plugin compatibility (generated).
 */
public class ServerLoadEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public ServerLoadEvent() {
        super(false);
    }

    public ServerLoadEvent(Object... ctx) {
        super(false);
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
