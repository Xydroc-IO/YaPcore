package com.yapcore.yap420.plant;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Concurrent plot index keyed by world|x|y|z. */
public final class PlotRegistry {

    private final ConcurrentHashMap<String, PlotState> byKey = new ConcurrentHashMap<>();

    public void clear() {
        byKey.clear();
    }

    public void put(PlotState plot) {
        byKey.put(plot.key(), plot);
    }

    public Optional<PlotState> get(String world, int x, int y, int z) {
        return Optional.ofNullable(byKey.get(PlotState.keyOf(world, x, y, z)));
    }

    public Optional<PlotState> remove(String world, int x, int y, int z) {
        return Optional.ofNullable(byKey.remove(PlotState.keyOf(world, x, y, z)));
    }

    public boolean contains(String world, int x, int y, int z) {
        return byKey.containsKey(PlotState.keyOf(world, x, y, z));
    }

    public Collection<PlotState> all() {
        return List.copyOf(byKey.values());
    }

    public List<PlotState> inChunk(String world, int chunkX, int chunkZ) {
        List<PlotState> out = new ArrayList<>();
        for (PlotState plot : byKey.values()) {
            if (plot.world().equals(world) && plot.chunkX() == chunkX && plot.chunkZ() == chunkZ) {
                out.add(plot);
            }
        }
        return out;
    }

    public int size() {
        return byKey.size();
    }
}
