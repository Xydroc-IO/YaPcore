package me.clip.placeholderapi.events;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class ExpansionRegisterEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final PlaceholderExpansion expansion;
    private boolean cancelled;

    public ExpansionRegisterEvent(@NotNull PlaceholderExpansion expansion) {
        this.expansion = expansion;
    }

    @NotNull
    public PlaceholderExpansion getExpansion() {
        return expansion;
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
    @NotNull
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    @NotNull
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
