package com.destroystokyo.paper.event.player;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Paper/Bukkit event stub (YaPcore). */
public class PlayerTeleportEndGatewayEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public PlayerTeleportEndGatewayEvent() { super(false); }
    public PlayerTeleportEndGatewayEvent(Object... ctx) { super(false); }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
