package com.yapcore.yap420.press;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Concurrent packaging-press index. */
public final class PressRegistry {

    private final ConcurrentHashMap<String, PressState> byKey = new ConcurrentHashMap<>();

    public void clear() {
        byKey.clear();
    }

    public void put(PressState press) {
        byKey.put(press.key(), press);
    }

    public Optional<PressState> get(String world, int x, int y, int z) {
        return Optional.ofNullable(byKey.get(PressState.keyOf(world, x, y, z)));
    }

    public Optional<PressState> remove(String world, int x, int y, int z) {
        return Optional.ofNullable(byKey.remove(PressState.keyOf(world, x, y, z)));
    }

    public boolean contains(String world, int x, int y, int z) {
        return byKey.containsKey(PressState.keyOf(world, x, y, z));
    }

    public Collection<PressState> all() {
        return List.copyOf(byKey.values());
    }

    public List<PressState> inChunk(String world, int chunkX, int chunkZ) {
        List<PressState> out = new ArrayList<>();
        for (PressState press : byKey.values()) {
            if (press.world().equals(world) && press.chunkX() == chunkX && press.chunkZ() == chunkZ) {
                out.add(press);
            }
        }
        return out;
    }

    public int size() {
        return byKey.size();
    }
}
