package com.yapcore.portals.store;

import org.bukkit.Location;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Wand selection drafts (pos1 / pos2) per admin. */
public final class SelectionDrafts {

    public record Corner(String world, int x, int y, int z) {
        public static Corner of(Location loc) {
            return new Corner(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        }
    }

    private final Map<UUID, Corner> pos1 = new ConcurrentHashMap<>();
    private final Map<UUID, Corner> pos2 = new ConcurrentHashMap<>();

    public void setPos1(UUID uuid, Corner corner) {
        pos1.put(uuid, corner);
    }

    public void setPos2(UUID uuid, Corner corner) {
        pos2.put(uuid, corner);
    }

    public Optional<Corner> pos1(UUID uuid) {
        return Optional.ofNullable(pos1.get(uuid));
    }

    public Optional<Corner> pos2(UUID uuid) {
        return Optional.ofNullable(pos2.get(uuid));
    }

    public void clear(UUID uuid) {
        pos1.remove(uuid);
        pos2.remove(uuid);
    }
}
