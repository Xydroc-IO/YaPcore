package com.yapcore.yap420.cure;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Concurrent drying-rack index. */
public final class RackRegistry {

    private final ConcurrentHashMap<String, RackState> byKey = new ConcurrentHashMap<>();

    public void clear() {
        byKey.clear();
    }

    public void put(RackState rack) {
        byKey.put(rack.key(), rack);
    }

    public Optional<RackState> get(String world, int x, int y, int z) {
        return Optional.ofNullable(byKey.get(RackState.keyOf(world, x, y, z)));
    }

    public Optional<RackState> remove(String world, int x, int y, int z) {
        return Optional.ofNullable(byKey.remove(RackState.keyOf(world, x, y, z)));
    }

    public boolean contains(String world, int x, int y, int z) {
        return byKey.containsKey(RackState.keyOf(world, x, y, z));
    }

    public Collection<RackState> all() {
        return List.copyOf(byKey.values());
    }

    public List<RackState> inChunk(String world, int chunkX, int chunkZ) {
        List<RackState> out = new ArrayList<>();
        for (RackState rack : byKey.values()) {
            if (rack.world().equals(world) && rack.chunkX() == chunkX && rack.chunkZ() == chunkZ) {
                out.add(rack);
            }
        }
        return out;
    }

    public int size() {
        return byKey.size();
    }
}
