package com.yapcore.vehicles.api;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Public service for real vehicle mechanics (not minecarts/boats).
 * <p>
 * Obtain via:
 * {@code Bukkit.getServicesManager().load(VehicleAPI.class)}
 * after soft-depending on {@code YaPVehicles}.
 */
public interface VehicleAPI {

    /** Register a custom vehicle type. Ids are case-insensitive. */
    void registerType(VehicleType type);

    /** Unregister a type previously registered by a plugin. Built-ins may be removed too. */
    boolean unregisterType(String typeId);

    Optional<VehicleType> getType(String typeId);

    Collection<VehicleType> getTypes();

    /**
     * Spawn a vehicle at {@code location} facing that location's yaw.
     *
     * @param owner optional owner (metadata / destroy permissions); may be null
     */
    Vehicle spawn(Location location, String typeId, @Nullable Player owner);

    Optional<Vehicle> getVehicle(UUID vehicleId);

    /** Resolve a YaP vehicle from its chassis entity (or a seat / foreign visual). */
    Optional<Vehicle> getByEntity(Entity entity);

    /** Vehicle the player is currently riding, if any. */
    Optional<Vehicle> getByPassenger(Player player);

    Collection<Vehicle> getVehicles();

    boolean destroy(Vehicle vehicle, boolean dropItem);

    /** Compatibility bridge for remapping other plugins' minecart/boat vehicles. */
    VehicleCompatAPI compat();

    /** Upgrade parts API (craft / shop / install). */
    VehicleUpgradeAPI upgrades();
}
