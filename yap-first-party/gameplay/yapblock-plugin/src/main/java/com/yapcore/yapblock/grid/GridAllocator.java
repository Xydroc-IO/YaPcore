package com.yapcore.yapblock.grid;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Allocates the next free spiral grid slot. */
public final class GridAllocator {

    private final IslandGrid grid;
    private final AtomicInteger nextSlot = new AtomicInteger(0);
    private final Set<String> occupied = ConcurrentHashMap.newKeySet();

    public GridAllocator(IslandGrid grid) {
        this.grid = grid;
    }

    public void markOccupied(int gx, int gz) {
        occupied.add(grid.key(gx, gz));
    }

    public void release(int gx, int gz) {
        occupied.remove(grid.key(gx, gz));
    }

    public synchronized int[] allocate() {
        while (true) {
            int slot = nextSlot.getAndIncrement();
            int[] coord = grid.slotToGrid(slot);
            String key = grid.key(coord[0], coord[1]);
            if (occupied.add(key)) {
                return coord;
            }
        }
    }

    public void seedFromMaxSlot(int maxKnownSlot) {
        nextSlot.updateAndGet(cur -> Math.max(cur, maxKnownSlot + 1));
    }
}
