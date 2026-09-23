package com.yapcore.claims;

import java.util.Locale;
import java.util.Optional;

/** Pure helpers for claiming the next grid plot next to owned land. */
public final class ClaimExpandRules {

    public enum Dir {
        NORTH(0, -1, "north"),
        SOUTH(0, 1, "south"),
        WEST(-1, 0, "west"),
        EAST(1, 0, "east");

        private final int dx;
        private final int dz;
        private final String label;

        Dir(int dx, int dz, String label) {
            this.dx = dx;
            this.dz = dz;
            this.label = label;
        }

        public int dx() {
            return dx;
        }

        public int dz() {
            return dz;
        }

        public String label() {
            return label;
        }
    }

    public record Plot(int minX, int maxX, int minZ, int maxZ) {
        public int centerX() {
            return minX + (maxX - minX) / 2;
        }

        public int centerZ() {
            return minZ + (maxZ - minZ) / 2;
        }
    }

    private ClaimExpandRules() {
    }

    /** Aligned plot containing block XZ for a given plot size. */
    public static Plot plotAt(int blockX, int blockZ, int plotSize) {
        int size = Math.max(1, plotSize);
        int minX = Math.floorDiv(blockX, size) * size;
        int minZ = Math.floorDiv(blockZ, size) * size;
        return new Plot(minX, minX + size - 1, minZ, minZ + size - 1);
    }

    /** Neighbor plot one grid step in {@code dir}. */
    public static Plot adjacent(Plot from, Dir dir, int plotSize) {
        int size = Math.max(1, plotSize);
        return new Plot(
                from.minX() + dir.dx() * size,
                from.maxX() + dir.dx() * size,
                from.minZ() + dir.dz() * size,
                from.maxZ() + dir.dz() * size);
    }

    /**
     * Facing compass from yaw (Minecraft: 0 = south, 90 = west, …).
     * Ties break toward the larger absolute component.
     */
    public static Dir fromYaw(float yaw) {
        float rot = yaw % 360f;
        if (rot < 0f) {
            rot += 360f;
        }
        if (rot >= 315f || rot < 45f) {
            return Dir.SOUTH;
        }
        if (rot < 135f) {
            return Dir.WEST;
        }
        if (rot < 225f) {
            return Dir.NORTH;
        }
        return Dir.EAST;
    }

    public static Optional<Dir> parseDir(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "n", "north", "-z" -> Optional.of(Dir.NORTH);
            case "s", "south", "+z" -> Optional.of(Dir.SOUTH);
            case "w", "west", "-x" -> Optional.of(Dir.WEST);
            case "e", "east", "+x" -> Optional.of(Dir.EAST);
            default -> Optional.empty();
        };
    }

    /** True if rectangles share an edge (not only a corner) or overlap. */
    public static boolean sharesEdgeOrOverlaps(
            int aMinX, int aMaxX, int aMinZ, int aMaxZ,
            int bMinX, int bMaxX, int bMinZ, int bMaxZ) {
        boolean xOverlap = aMinX <= bMaxX && aMaxX >= bMinX;
        boolean zOverlap = aMinZ <= bMaxZ && aMaxZ >= bMinZ;
        if (xOverlap && zOverlap) {
            return true;
        }
        // Touching on X edge (same Z span overlap), adjacent columns
        if (zOverlap && (aMaxX + 1 == bMinX || bMaxX + 1 == aMinX)) {
            return true;
        }
        // Touching on Z edge (same X span overlap), adjacent rows
        if (xOverlap && (aMaxZ + 1 == bMinZ || bMaxZ + 1 == aMinZ)) {
            return true;
        }
        return false;
    }
}
