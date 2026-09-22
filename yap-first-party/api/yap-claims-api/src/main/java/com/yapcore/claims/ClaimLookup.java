package com.yapcore.claims;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.List;
import java.util.Optional;

/**
 * Claim land lookup for first-party plugins (wilderness regen, disasters, map, …).
 * Provided by {@code YaPClaims} via {@code ServicesManager} when claims are enabled.
 */
public interface ClaimLookup {

    /** Whether claims are loaded and protecting land on this backend. */
    boolean enabled();

    /** Claim covering this block, if any. */
    Optional<ClaimInfo> at(Location location);

    default Optional<ClaimInfo> at(World world, int x, int z) {
        if (world == null) {
            return Optional.empty();
        }
        return at(new Location(world, x + 0.5, 64, z + 0.5));
    }

    /** True when no player claim covers the location. */
    default boolean isWilderness(Location location) {
        return at(location).isEmpty();
    }

    /**
     * Whether environment / system code may alter blocks here.
     * Claimed land is protected; wilderness is allowed.
     */
    default boolean canSystemModify(Location location) {
        return isWilderness(location);
    }

    /** Local (this server-id) claims currently loaded in memory. */
    default List<ClaimInfo> localClaims() {
        return List.of();
    }
}
