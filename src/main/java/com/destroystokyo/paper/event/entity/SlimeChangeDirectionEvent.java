package com.destroystokyo.paper.event.entity;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Paper/Bukkit event stub (YaPcore). */
public class SlimeChangeDirectionEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public SlimeChangeDirectionEvent() { super(false); }
    public SlimeChangeDirectionEvent(Object... ctx) { super(false); }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
