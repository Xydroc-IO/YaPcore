package com.yapcore.vehicles.api;

import org.bukkit.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * Optional hook for third-party / host-specific vehicle plugin detection.
 * Return a YaP type id to force a mapping, empty to abstain, or {@code "skip"} to never claim.
 */
@FunctionalInterface
public interface VehicleCompatHook {

    /**
     * @return YaP type id, empty if this hook has no opinion, or {@code Optional.of("skip")}
     */
    java.util.Optional<String> mapType(Entity foreign);

    /** Optional: true if this entity belongs to a vehicle plugin you understand. */
    default boolean isForeignVehicle(Entity foreign) {
        return mapType(foreign).filter(id -> !"skip".equalsIgnoreCase(id)).isPresent();
    }

    default @Nullable String pluginName() {
        return null;
    }
}
