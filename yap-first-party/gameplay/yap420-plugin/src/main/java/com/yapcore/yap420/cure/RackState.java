package com.yapcore.yap420.cure;

import com.yapcore.yap420.plant.StrainId;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Drying rack world state. */
public record RackState(
        String world,
        int x,
        int y,
        int z,
        List<Slot> slots,
        UUID entityUuid
) {
    public RackState {
        Objects.requireNonNull(world, "world");
        slots = List.copyOf(slots == null ? List.of() : slots);
    }

    public String key() {
        return keyOf(world, x, y, z);
    }

    public static String keyOf(String world, int x, int y, int z) {
        return world + "|" + x + "|" + y + "|" + z;
    }

    public int chunkX() {
        return x >> 4;
    }

    public int chunkZ() {
        return z >> 4;
    }

    public RackState withEntity(UUID uuid) {
        return new RackState(world, x, y, z, slots, uuid);
    }

    public RackState withSlots(List<Slot> next) {
        return new RackState(world, x, y, z, next, entityUuid);
    }

    public int occupied() {
        return slots.size();
    }

    public List<Slot> mutableSlots() {
        return new ArrayList<>(slots);
    }

    /** One wet bud drying on the rack. */
    public record Slot(StrainId strain, long depositedAtMs, boolean cured) {
        public Slot {
            Objects.requireNonNull(strain, "strain");
        }

        public Slot markCured() {
            return new Slot(strain, depositedAtMs, true);
        }
    }
}
