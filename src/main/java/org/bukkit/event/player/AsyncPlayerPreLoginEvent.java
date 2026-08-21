package org.bukkit.event.player;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Paper/Bukkit event stub for YaPcore plugin compatibility (generated).
 */
public class AsyncPlayerPreLoginEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public AsyncPlayerPreLoginEvent() {
        super(true);
    }

    public AsyncPlayerPreLoginEvent(Object... ctx) {
        super(true);
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
