package io.papermc.paper.event.server;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Cancellable;

/**
 * Paper/Bukkit event stub for YaPcore plugin compatibility (generated).
 */
public class WhitelistStateUpdateEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled;

    public WhitelistStateUpdateEvent() {
        super(false);
    }

    public WhitelistStateUpdateEvent(Object... ctx) {
        super(false);
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
