package com.yapcore.regions;

import org.bukkit.Location;

import java.util.Optional;

/** Admin-defined cuboid regions (staff / server templates). */
public interface RegionService {

    Optional<AdminRegion> at(Location location);

    Optional<AdminRegion> named(String name);

    FlagValue flagAt(Location location, RegionFlag flag);
}
