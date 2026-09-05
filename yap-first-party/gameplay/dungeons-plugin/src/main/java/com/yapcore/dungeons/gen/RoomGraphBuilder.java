package com.yapcore.dungeons.gen;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Deterministic room graph planner (no Bukkit). */
public final class RoomGraphBuilder {

    public enum RoomKind {
        ENTRANCE, COMBAT, TREASURE, TRAP, BOSS
    }

    public record Room(int id, RoomKind kind, int x, int z, int sizeX, int sizeZ) {
    }

    public record Corridor(int fromId, int toId) {
    }

    public record Layout(List<Room> rooms, List<Corridor> corridors, int originY) {
    }

    public Layout build(long seed, int roomCount) {
        Random rng = new Random(seed);
        int rooms = Math.max(4, Math.min(24, roomCount));
        List<Room> list = new ArrayList<>();
        List<Corridor> corridors = new ArrayList<>();
        int y = 64;
        int cursorX = 0;
        int cursorZ = 0;
        list.add(new Room(0, RoomKind.ENTRANCE, cursorX, cursorZ, 11, 11));
        for (int i = 1; i < rooms - 1; i++) {
            RoomKind kind;
            int roll = rng.nextInt(100);
            if (roll < 15) {
                kind = RoomKind.TREASURE;
            } else if (roll < 30) {
                kind = RoomKind.TRAP;
            } else {
                kind = RoomKind.COMBAT;
            }
            int size = 9 + rng.nextInt(5);
            // Alternate axis to create a meandering path with occasional side branch
            boolean east = rng.nextBoolean();
            if (east) {
                cursorX += 14 + rng.nextInt(6);
            } else {
                cursorZ += 14 + rng.nextInt(6);
            }
            // Side branch offset
            int ox = east ? 0 : (rng.nextBoolean() ? rng.nextInt(8) : -rng.nextInt(8));
            int oz = east ? (rng.nextBoolean() ? rng.nextInt(8) : -rng.nextInt(8)) : 0;
            list.add(new Room(i, kind, cursorX + ox, cursorZ + oz, size, size));
            corridors.add(new Corridor(i - 1, i));
            if (i > 2 && rng.nextInt(100) < 25) {
                // Extra link to earlier room for loops
                int back = rng.nextInt(i - 1);
                corridors.add(new Corridor(back, i));
            }
        }
        int bossSize = 15;
        cursorX += 18;
        cursorZ += 4;
        list.add(new Room(rooms - 1, RoomKind.BOSS, cursorX, cursorZ, bossSize, bossSize));
        corridors.add(new Corridor(rooms - 2, rooms - 1));
        return new Layout(List.copyOf(list), List.copyOf(corridors), y);
    }
}
