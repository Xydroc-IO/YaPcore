package com.destroystokyo.paper.event.profile;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Paper/Bukkit event stub (YaPcore). */
public class PreFillProfileEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public PreFillProfileEvent() { super(false); }
    public PreFillProfileEvent(Object... ctx) { super(false); }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
