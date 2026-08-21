package org.bukkit.event.raid;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Paper/Bukkit event stub for YaPcore plugin compatibility (generated).
 */
public class RaidStopEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public RaidStopEvent() {
        super(false);
    }

    public RaidStopEvent(Object... ctx) {
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
