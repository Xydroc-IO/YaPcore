package com.yapcore.vehicles.api;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A live vehicle instance with real steering / throttle / seat mechanics.
 */
public interface Vehicle {

    UUID getId();

    VehicleType getType();

    /** Primary chassis entity (usually an invisible armor stand). */
    Entity getChassis();

    Location getLocation();

    /** Body yaw in degrees (driving direction). */
    float getYaw();

    /** Signed forward speed in blocks/tick (negative = reverse). */
    double getSpeed();

    /** Sideways slip speed in local X (not in vanilla). */
    double getLateralSpeed();

    Vector getVelocity();

    @Nullable
    Player getDriver();

    List<Player> getOccupants();

    boolean isEmpty();

    double getFuel();

    double getMaxFuel();

    /** @return amount actually added */
    double refuel(double amount);

    double getHealth();

    double getMaxHealth();

    /**
     * Apply damage. Returns true if the vehicle was destroyed.
     */
    boolean damage(double amount, String cause);

    /**
     * Seat a player. {@code seatIndex} -1 picks first free seat (driver preferred).
     *
     * @return false if full, wrong world, or cancelled
     */
    boolean enter(Player player, int seatIndex);

    boolean exit(Player player);

    /** Force-eject everyone and remove entities. */
    void destroy(boolean dropItem);

    Optional<Integer> seatOf(Player player);
}
