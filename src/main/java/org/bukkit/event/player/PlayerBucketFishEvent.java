package org.bukkit.event.player;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Paper/Bukkit event stub (YaPcore). */
public class PlayerBucketFishEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public PlayerBucketFishEvent() { super(false); }
    public PlayerBucketFishEvent(Object... ctx) { super(false); }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
