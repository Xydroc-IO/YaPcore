package com.yapcore.yapblock.grid;

import com.yapcore.yapblock.IslandSnapshot;
import com.yapcore.yapblock.YapblockConfig;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Concurrent island lookups by id / owner / grid key / world location. */
public final class IslandIndex {

    private final YapblockConfig config;
    private final IslandGrid grid;
    private final Map<Long, IslandSnapshot> byId = new ConcurrentHashMap<>();
    private final Map<UUID, Long> ownerToIsland = new ConcurrentHashMap<>();
    private final Map<String, Long> gridToIsland = new ConcurrentHashMap<>();
    private final Map<UUID, Long> memberToIsland = new ConcurrentHashMap<>();

    public IslandIndex(YapblockConfig config, IslandGrid grid) {
        this.config = config;
        this.grid = grid;
    }

    public void clear() {
        byId.clear();
        ownerToIsland.clear();
        gridToIsland.clear();
        memberToIsland.clear();
    }

    public void put(IslandSnapshot island) {
        byId.put(island.id(), island);
        ownerToIsland.put(island.ownerId(), island.id());
        gridToIsland.put(grid.key(island.gridX(), island.gridZ()), island.id());
        memberToIsland.put(island.ownerId(), island.id());
    }

    public void bindMember(UUID playerId, long islandId) {
        memberToIsland.put(playerId, islandId);
    }

    public void unbindMember(UUID playerId) {
        memberToIsland.remove(playerId);
    }

    public void remove(long islandId) {
        IslandSnapshot snap = byId.remove(islandId);
        if (snap == null) {
            return;
        }
        ownerToIsland.remove(snap.ownerId(), islandId);
        gridToIsland.remove(grid.key(snap.gridX(), snap.gridZ()), islandId);
        memberToIsland.entrySet().removeIf(e -> e.getValue() == islandId);
    }

    public Optional<IslandSnapshot> byId(long id) {
        return Optional.ofNullable(byId.get(id));
    }

    public Optional<IslandSnapshot> byOwner(UUID ownerId) {
        Long id = ownerToIsland.get(ownerId);
        return id == null ? Optional.empty() : byId(id);
    }

    public Optional<IslandSnapshot> ofPlayer(UUID playerId) {
        Long id = memberToIsland.get(playerId);
        if (id != null) {
            return byId(id);
        }
        return byOwner(playerId);
    }

    public Optional<IslandSnapshot> at(Location location) {
        if (location == null || location.getWorld() == null) {
            return Optional.empty();
        }
        if (!location.getWorld().getName().equalsIgnoreCase(config.worldName())) {
            return Optional.empty();
        }
        int[] nearest = grid.worldToNearestGrid(location.getX(), location.getZ());
        Long id = gridToIsland.get(grid.key(nearest[0], nearest[1]));
        if (id == null) {
            return Optional.empty();
        }
        IslandSnapshot snap = byId.get(id);
        if (snap == null) {
            return Optional.empty();
        }
        double cx = grid.worldX(snap.gridX());
        double cz = grid.worldZ(snap.gridZ());
        double dx = location.getX() - cx;
        double dz = location.getZ() - cz;
        if ((dx * dx + dz * dz) > (double) snap.sizeRadius() * snap.sizeRadius()) {
            return Optional.empty();
        }
        return Optional.of(snap);
    }

    public Collection<IslandSnapshot> all() {
        return byId.values();
    }

    public List<IslandSnapshot> topByLevel(int limit) {
        List<IslandSnapshot> list = new ArrayList<>(byId.values());
        list.sort(Comparator.comparingLong(IslandSnapshot::level).reversed()
                .thenComparingLong(IslandSnapshot::id));
        if (limit > 0 && list.size() > limit) {
            return List.copyOf(list.subList(0, limit));
        }
        return List.copyOf(list);
    }

    public Location homeLocation(World world, IslandSnapshot island) {
        return new Location(world, island.homeX(), island.homeY(), island.homeZ());
    }

    public Location centerLocation(World world, IslandSnapshot island) {
        return new Location(world, grid.worldX(island.gridX()) + 0.5,
                grid.pasteY() + 1, grid.worldZ(island.gridZ()) + 0.5);
    }
}
