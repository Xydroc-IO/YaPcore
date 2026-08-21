package org.bukkit.event.entity;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Paper/Bukkit event stub for YaPcore plugin compatibility (generated).
 */
public class CreatureSpawnEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public CreatureSpawnEvent() {
        super(false);
    }

    public CreatureSpawnEvent(Object... ctx) {
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
