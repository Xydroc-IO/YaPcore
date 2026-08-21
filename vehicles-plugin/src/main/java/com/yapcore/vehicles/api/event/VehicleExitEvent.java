package com.yapcore.vehicles.api.event;

import com.yapcore.vehicles.api.Vehicle;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class VehicleExitEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Vehicle vehicle;
    private final Player player;
    private boolean cancelled;

    public VehicleExitEvent(Vehicle vehicle, Player player) {
        this.vehicle = vehicle;
        this.player = player;
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    public Player getPlayer() {
        return player;
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
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
