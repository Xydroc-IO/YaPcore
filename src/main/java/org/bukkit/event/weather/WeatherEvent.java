package org.bukkit.event.weather;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Paper/Bukkit event stub (YaPcore). */
public class WeatherEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public WeatherEvent() { super(false); }
    public WeatherEvent(Object... ctx) { super(false); }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
