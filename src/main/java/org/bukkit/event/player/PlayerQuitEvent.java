package org.bukkit.event.player;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

public class PlayerQuitEvent extends PlayerEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private String quitMessage;

    public PlayerQuitEvent(Player player, String quitMessage) {
        super(player);
        this.quitMessage = quitMessage;
    }

    public String getQuitMessage() { return quitMessage; }
    public void setQuitMessage(String quitMessage) { this.quitMessage = quitMessage; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
