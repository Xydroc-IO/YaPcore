package org.bukkit.event.player;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

public class PlayerJoinEvent extends PlayerEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private String joinMessage;

    public PlayerJoinEvent(Player player, String joinMessage) {
        super(player);
        this.joinMessage = joinMessage;
    }

    public String getJoinMessage() { return joinMessage; }
    public void setJoinMessage(String joinMessage) { this.joinMessage = joinMessage; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
