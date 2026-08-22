package com.yapcore.essentials.store;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BackStore {

    private final Map<UUID, Location> lastLocations = new ConcurrentHashMap<>();

    public void remember(Player player) {
        lastLocations.put(player.getUniqueId(), player.getLocation().clone());
    }

    public Location back(Player player) {
        return lastLocations.get(player.getUniqueId());
    }
}
