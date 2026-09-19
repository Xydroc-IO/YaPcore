package com.yapcore.holo;

import org.bukkit.Location;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * DecentHolograms-class packet holograms. Provided by {@code YaPHolo} via ServicesManager.
 */
public interface HologramService {

    Hologram create(String id, Location location, List<String> lines);

    Optional<Hologram> get(String id);

    boolean delete(String id);

    Collection<Hologram> all();

    void save();

    void reload();

    boolean enabled();
}
