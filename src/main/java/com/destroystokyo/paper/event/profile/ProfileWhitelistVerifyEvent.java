package com.destroystokyo.paper.event.profile;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Paper/Bukkit event stub (YaPcore). */
public class ProfileWhitelistVerifyEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public ProfileWhitelistVerifyEvent() { super(false); }
    public ProfileWhitelistVerifyEvent(Object... ctx) { super(false); }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
