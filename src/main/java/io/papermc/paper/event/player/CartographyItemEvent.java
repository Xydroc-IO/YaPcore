package io.papermc.paper.event.player;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Paper/Bukkit event stub (YaPcore). */
public class CartographyItemEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public CartographyItemEvent() { super(false); }
    public CartographyItemEvent(Object... ctx) { super(false); }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
