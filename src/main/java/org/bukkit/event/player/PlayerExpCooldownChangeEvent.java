package org.bukkit.event.player;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Paper/Bukkit event stub for YaPcore plugin compatibility (generated).
 */
public class PlayerExpCooldownChangeEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public PlayerExpCooldownChangeEvent() {
        super(false);
    }

    public PlayerExpCooldownChangeEvent(Object... ctx) {
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
