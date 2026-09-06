package com.yapcore.dungeons.gen;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Deterministic non-overlapping room graph.
 * Rooms are axis-aligned boxes with a minimum gap; corridors link centers.
 */
public final class RoomGraphBuilder {

    public static final int PAD = 6;
    public static final int ROOM_Y = 64;
    public static final int ROOM_HEIGHT = 6; // floor..ceiling inclusive span used by carver

    public enum RoomKind {
        ENTRANCE, COMBAT, TREASURE, TRAP, BOSS
    }

    public record Room(int id, RoomKind kind, int x, int z, int sizeX, int sizeZ) {
        public int centerX() {
            return x + sizeX / 2;
        }

        public int centerZ() {
            return z + sizeZ / 2;
        }

        public boolean overlaps(Room other, int pad) {
            return x - pad < other.x + other.sizeX
                    && x + sizeX + pad > other.x
                    && z - pad < other.z + other.sizeZ
                    && z + sizeZ + pad > other.z;
        }
    }

    public record Corridor(int fromId, int toId) {
    }

    public record Layout(List<Room> rooms, List<Corridor> corridors, int originY) {
        public int minX() {
            return rooms.stream().mapToInt(Room::x).min().orElse(0);
        }

        public int minZ() {
            return rooms.stream().mapToInt(Room::z).min().orElse(0);
        }

        public int maxX() {
            return rooms.stream().mapToInt(r -> r.x() + r.sizeX()).max().orElse(0);
        }

        public int maxZ() {
            return rooms.stream().mapToInt(r -> r.z() + r.sizeZ()).max().orElse(0);
        }
    }

    public Layout build(long seed, int roomCount) {
        Random rng = new Random(seed);
        int target = Math.max(4, Math.min(24, roomCount));
        List<Room> rooms = new ArrayList<>();
        List<Corridor> corridors = new ArrayList<>();

        rooms.add(new Room(0, RoomKind.ENTRANCE, 0, 0, 13, 13));

        int[][] dirs = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
        for (int i = 1; i < target - 1; i++) {
            RoomKind kind = rollKind(rng);
            int size = 11 + rng.nextInt(5);
            Room placed = null;
            Room anchor = null;
            for (int attempt = 0; attempt < 48 && placed == null; attempt++) {
                anchor = rooms.get(rng.nextInt(rooms.size()));
                int[] dir = dirs[rng.nextInt(dirs.length)];
                int gap = PAD + 2 + rng.nextInt(3);
                int nx;
                int nz;
                if (dir[0] > 0) {
                    nx = anchor.x() + anchor.sizeX() + gap;
                    nz = anchor.z() + rng.nextInt(Math.max(1, anchor.sizeZ() - size + 1));
                } else if (dir[0] < 0) {
                    nx = anchor.x() - gap - size;
                    nz = anchor.z() + rng.nextInt(Math.max(1, anchor.sizeZ() - size + 1));
                } else if (dir[1] > 0) {
                    nx = anchor.x() + rng.nextInt(Math.max(1, anchor.sizeX() - size + 1));
                    nz = anchor.z() + anchor.sizeZ() + gap;
                } else {
                    nx = anchor.x() + rng.nextInt(Math.max(1, anchor.sizeX() - size + 1));
                    nz = anchor.z() - gap - size;
                }
                Room candidate = new Room(i, kind, nx, nz, size, size);
                if (fits(rooms, candidate)) {
                    placed = candidate;
                }
            }
            if (placed == null) {
                // Fallback: push east of farthest room
                Room last = rooms.getLast();
                placed = new Room(i, kind, last.x() + last.sizeX() + PAD + 3, last.z(), size, size);
                int guard = 0;
                while (!fits(rooms, placed) && guard++ < 20) {
                    placed = new Room(i, kind, placed.x() + PAD + 2, placed.z(), size, size);
                }
                anchor = last;
            }
            rooms.add(placed);
            corridors.add(new Corridor(anchor.id(), placed.id()));
            // Occasional extra link to a nearby existing room (non-overlapping path still ok)
            if (i > 2 && rng.nextInt(100) < 20) {
                Room near = findNearest(rooms, placed, placed.id());
                if (near != null && near.id() != anchor.id()) {
                    corridors.add(new Corridor(near.id(), placed.id()));
                }
            }
        }

        int bossSize = 17;
        Room lastCombat = rooms.getLast();
        Room boss = new Room(target - 1, RoomKind.BOSS,
                lastCombat.x() + lastCombat.sizeX() + PAD + 4,
                lastCombat.z() - 2,
                bossSize, bossSize);
        int guard = 0;
        while (!fits(rooms, boss) && guard++ < 30) {
            boss = new Room(target - 1, RoomKind.BOSS, boss.x() + PAD + 2, boss.z(), bossSize, bossSize);
        }
        rooms.add(boss);
        corridors.add(new Corridor(lastCombat.id(), boss.id()));

        return new Layout(List.copyOf(rooms), List.copyOf(corridors), ROOM_Y);
    }

    private static RoomKind rollKind(Random rng) {
        int roll = rng.nextInt(100);
        if (roll < 18) {
            return RoomKind.TREASURE;
        }
        if (roll < 32) {
            return RoomKind.TRAP;
        }
        return RoomKind.COMBAT;
    }

    private static boolean fits(List<Room> existing, Room candidate) {
        for (Room r : existing) {
            if (candidate.overlaps(r, PAD)) {
                return false;
            }
        }
        return true;
    }

    private static Room findNearest(List<Room> rooms, Room target, int skipId) {
        Room best = null;
        long bestDist = Long.MAX_VALUE;
        for (Room r : rooms) {
            if (r.id() == skipId || r.id() == target.id()) {
                continue;
            }
            long dx = (long) r.centerX() - target.centerX();
            long dz = (long) r.centerZ() - target.centerZ();
            long d = dx * dx + dz * dz;
            if (d < bestDist) {
                bestDist = d;
                best = r;
            }
        }
        return best;
    }
}
