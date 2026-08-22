package com.yapcore.vehicles.api.event;

import com.yapcore.vehicles.api.Vehicle;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class VehicleSpawnEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Vehicle vehicle;
    private final @Nullable Player owner;
    private boolean cancelled;

    public VehicleSpawnEvent(Vehicle vehicle, @Nullable Player owner) {
        this.vehicle = vehicle;
        this.owner = owner;
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    public @Nullable Player getOwner() {
        return owner;
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
