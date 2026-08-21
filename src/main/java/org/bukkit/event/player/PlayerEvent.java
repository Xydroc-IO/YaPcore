package org.bukkit.event.player;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class PlayerEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    protected final Player player;

    public PlayerEvent(Player player) {
        this(player, false);
    }

    public PlayerEvent(Player player, boolean async) {
        super(async);
        this.player = player;
    }

    public Player getPlayer() {
        return player;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
