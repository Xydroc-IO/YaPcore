package me.clip.placeholderapi.events;

import java.util.Collections;
import java.util.List;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class ExpansionsLoadedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final List<PlaceholderExpansion> expansions;

    public ExpansionsLoadedEvent(@NotNull List<PlaceholderExpansion> expansions) {
        this.expansions = Collections.unmodifiableList(expansions);
    }

    @NotNull
    public List<PlaceholderExpansion> getExpansions() {
        return expansions;
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
