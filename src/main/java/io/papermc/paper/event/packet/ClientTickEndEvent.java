package io.papermc.paper.event.packet;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Paper/Bukkit event stub (YaPcore). */
public class ClientTickEndEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public ClientTickEndEvent() { super(false); }
    public ClientTickEndEvent(Object... ctx) { super(false); }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
