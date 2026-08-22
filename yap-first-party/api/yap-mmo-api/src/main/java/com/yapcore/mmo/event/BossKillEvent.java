package com.yapcore.mmo.event;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/** Fired when a player kills a YaP boss (PDC {@code yap_boss_id}). */
public final class BossKillEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String bossId;

    public BossKillEvent(Player player, String bossId) {
        super(player);
        this.bossId = bossId;
    }

    public String bossId() {
        return bossId;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
