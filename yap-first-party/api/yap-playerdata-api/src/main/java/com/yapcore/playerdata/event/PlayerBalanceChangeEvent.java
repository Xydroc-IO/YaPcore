package com.yapcore.playerdata.event;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a player's YaP economy balance changes (deposit / withdraw / set).
 * Positive {@link #delta()} means money was added (earn).
 */
public final class PlayerBalanceChangeEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final double oldBalance;
    private final double newBalance;
    private final double delta;

    public PlayerBalanceChangeEvent(Player player, double oldBalance, double newBalance) {
        super(player);
        this.oldBalance = oldBalance;
        this.newBalance = newBalance;
        this.delta = newBalance - oldBalance;
    }

    public double oldBalance() {
        return oldBalance;
    }

    public double newBalance() {
        return newBalance;
    }

    /** Signed change; positive = earn. */
    public double delta() {
        return delta;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
