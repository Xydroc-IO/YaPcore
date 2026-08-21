package com.yapcore.vehicles.api.event;

import com.yapcore.vehicles.api.Vehicle;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class VehicleDestroyEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Vehicle vehicle;
    private final boolean dropItem;
    private boolean cancelled;

    public VehicleDestroyEvent(Vehicle vehicle, boolean dropItem) {
        this.vehicle = vehicle;
        this.dropItem = dropItem;
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    public boolean isDropItem() {
        return dropItem;
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
