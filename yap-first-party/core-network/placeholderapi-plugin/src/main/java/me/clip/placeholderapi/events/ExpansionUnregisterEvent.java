package me.clip.placeholderapi.events;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class ExpansionUnregisterEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final PlaceholderExpansion expansion;

    public ExpansionUnregisterEvent(@NotNull PlaceholderExpansion expansion) {
        this.expansion = expansion;
    }

    @NotNull
    public PlaceholderExpansion getExpansion() {
        return expansion;
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
