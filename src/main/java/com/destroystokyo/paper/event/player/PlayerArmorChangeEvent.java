package com.destroystokyo.paper.event.player;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Paper/Bukkit event stub for YaPcore plugin compatibility (generated).
 */
public class PlayerArmorChangeEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public PlayerArmorChangeEvent() {
        super(false);
    }

    public PlayerArmorChangeEvent(Object... ctx) {
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
