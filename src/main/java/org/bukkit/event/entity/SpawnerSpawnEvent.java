package org.bukkit.event.entity;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Paper/Bukkit event stub (YaPcore). */
public class SpawnerSpawnEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public SpawnerSpawnEvent() { super(false); }
    public SpawnerSpawnEvent(Object... ctx) { super(false); }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
