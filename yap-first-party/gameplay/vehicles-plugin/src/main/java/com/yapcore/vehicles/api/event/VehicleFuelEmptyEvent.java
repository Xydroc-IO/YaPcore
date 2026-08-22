package com.yapcore.vehicles.api.event;

import com.yapcore.vehicles.api.Vehicle;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class VehicleFuelEmptyEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Vehicle vehicle;

    public VehicleFuelEmptyEvent(Vehicle vehicle) {
        this.vehicle = vehicle;
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
