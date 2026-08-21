package com.destroystokyo.paper.event.entity;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Paper/Bukkit event stub (YaPcore). */
public class ThrownEggHatchEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public ThrownEggHatchEvent() { super(false); }
    public ThrownEggHatchEvent(Object... ctx) { super(false); }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
