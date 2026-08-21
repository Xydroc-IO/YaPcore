package org.spigotmc.event.player;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Paper/Bukkit event stub (YaPcore). */
public class PlayerSpawnLocationEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public PlayerSpawnLocationEvent() { super(false); }
    public PlayerSpawnLocationEvent(Object... ctx) { super(false); }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
