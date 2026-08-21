package com.yapcore.vehicles.api.event;

import com.yapcore.vehicles.api.Vehicle;
import org.bukkit.block.Block;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class VehicleCollideEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Vehicle vehicle;
    private final @Nullable Block block;
    private final Vector impactVelocity;
    private boolean cancelled;

    public VehicleCollideEvent(Vehicle vehicle, @Nullable Block block, Vector impactVelocity) {
        this.vehicle = vehicle;
        this.block = block;
        this.impactVelocity = impactVelocity.clone();
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    public @Nullable Block getBlock() {
        return block;
    }

    public Vector getImpactVelocity() {
        return impactVelocity.clone();
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
