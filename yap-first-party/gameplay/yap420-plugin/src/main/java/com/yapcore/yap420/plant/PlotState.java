package com.yapcore.yap420.plant;

import java.util.Objects;
import java.util.UUID;

/** Immutable planted plot. */
public record PlotState(
        String world,
        int x,
        int y,
        int z,
        StrainId strain,
        int stage,
        long plantedAtMs,
        UUID entityUuid
) {
    public PlotState {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(strain, "strain");
        if (stage < 0) {
            throw new IllegalArgumentException("stage < 0");
        }
    }

    public String key() {
        return keyOf(world, x, y, z);
    }

    public static String keyOf(String world, int x, int y, int z) {
        return world + "|" + x + "|" + y + "|" + z;
    }

    public PlotState withStage(int nextStage, UUID uuid) {
        return new PlotState(world, x, y, z, strain, nextStage, plantedAtMs, uuid);
    }

    public PlotState withEntity(UUID uuid) {
        return new PlotState(world, x, y, z, strain, stage, plantedAtMs, uuid);
    }

    public int chunkX() {
        return x >> 4;
    }

    public int chunkZ() {
        return z >> 4;
    }
}
