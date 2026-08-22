package com.yapcore.mmo.event;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/** Fired when a player completes a YaP crafting recipe (recipe id from config). */
public final class ItemCraftedEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String recipeId;

    public ItemCraftedEvent(Player player, String recipeId) {
        super(player);
        this.recipeId = recipeId;
    }

    public String recipeId() {
        return recipeId;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
