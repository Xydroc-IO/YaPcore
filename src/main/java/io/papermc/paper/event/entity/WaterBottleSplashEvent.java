package io.papermc.paper.event.entity;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Paper/Bukkit event stub (YaPcore). */
public class WaterBottleSplashEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public WaterBottleSplashEvent() { super(false); }
    public WaterBottleSplashEvent(Object... ctx) { super(false); }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
