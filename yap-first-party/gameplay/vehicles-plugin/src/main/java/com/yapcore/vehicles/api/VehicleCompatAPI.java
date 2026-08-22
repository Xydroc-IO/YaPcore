package com.yapcore.vehicles.api;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Compatibility bridge — remap other plugins' minecart/boat vehicles onto YaP chassis + physics
 * while optionally keeping their entity as a visual (resource-pack / ModelEngine models).
 */
public interface VehicleCompatAPI {

    /**
     * Whether {@code entity} looks like a foreign plugin vehicle we should claim.
     */
    boolean shouldClaim(Entity entity);

    /**
     * Adapt a foreign minecart/boat (or similar) into a YaP vehicle.
     * Passengers are moved onto YaP seats. The original entity may be kept as a synced visual.
     *
     * @param typeId YaP type to use, or null to resolve from compat maps / default
     */
    Optional<Vehicle> adapt(Entity foreign, @Nullable Player driver, @Nullable String typeId);

    /** Register a plugin-specific detection / type-mapping hook. */
    void registerHook(VehicleCompatHook hook);

    void unregisterHook(VehicleCompatHook hook);
}
