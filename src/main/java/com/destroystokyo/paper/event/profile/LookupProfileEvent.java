package com.destroystokyo.paper.event.profile;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Paper/Bukkit event stub (YaPcore). */
public class LookupProfileEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public LookupProfileEvent() { super(false); }
    public LookupProfileEvent(Object... ctx) { super(false); }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
