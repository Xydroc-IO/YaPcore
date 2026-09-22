package com.yapcore.qol.timber;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.Set;

/**
 * Connected-log BFS for timber felling.
 * Region: SYNC — discovery is read-only on the origin region; breaks are scheduled by callers.
 */
public final class TreeFeller {

    private static final BlockFace[] NEIGHBORS = {
            BlockFace.UP, BlockFace.DOWN,
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST,
            BlockFace.NORTH_EAST, BlockFace.NORTH_WEST,
            BlockFace.SOUTH_EAST, BlockFace.SOUTH_WEST
    };

    private TreeFeller() {
    }

    public static boolean isLog(Material type) {
        return Tag.LOGS.isTagged(type) || Tag.LOGS_THAT_BURN.isTagged(type);
    }

    public static boolean isLeaf(Material type) {
        return Tag.LEAVES.isTagged(type);
    }

    /** Same wood family: OAK_LOG / OAK_WOOD / STRIPPED_OAK_LOG share "OAK". */
    public static String woodFamily(Material type) {
        String name = type.name();
        name = name.replace("STRIPPED_", "")
                .replace("_LOG", "")
                .replace("_WOOD", "")
                .replace("_STEM", "")
                .replace("_HYPHAE", "");
        return name.toUpperCase(Locale.ROOT);
    }

    public static boolean sameWoodFamily(Material a, Material b) {
        return woodFamily(a).equals(woodFamily(b));
    }

    /**
     * Collects connected logs matching {@code origin}'s wood family (excluding origin).
     */
    public static List<Block> collectLogs(Block origin, int maxLogs) {
        Material seed = origin.getType();
        if (!isLog(seed)) {
            return List.of();
        }
        String family = woodFamily(seed);
        List<Block> found = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        Queue<Block> queue = new ArrayDeque<>();
        seen.add(pack(origin));
        queue.add(origin);
        while (!queue.isEmpty() && found.size() < maxLogs) {
            Block cur = queue.poll();
            for (BlockFace face : NEIGHBORS) {
                Block next = cur.getRelative(face);
                long key = pack(next);
                if (!seen.add(key)) {
                    continue;
                }
                Material type = next.getType();
                if (!isLog(type) || !woodFamily(type).equals(family)) {
                    continue;
                }
                found.add(next);
                queue.add(next);
                if (found.size() >= maxLogs) {
                    break;
                }
            }
        }
        return found;
    }

    /**
     * Collects leaves near discovered logs (same wood family when name encodes it).
     */
    public static List<Block> collectLeaves(List<Block> logs, String family, int maxLeaves) {
        if (maxLeaves <= 0 || logs.isEmpty()) {
            return List.of();
        }
        List<Block> found = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (Block log : logs) {
            seen.add(pack(log));
        }
        Queue<Block> queue = new ArrayDeque<>(logs);
        while (!queue.isEmpty() && found.size() < maxLeaves) {
            Block cur = queue.poll();
            for (BlockFace face : NEIGHBORS) {
                Block next = cur.getRelative(face);
                long key = pack(next);
                if (!seen.add(key)) {
                    continue;
                }
                Material type = next.getType();
                if (!isLeaf(type)) {
                    continue;
                }
                String leafFamily = type.name()
                        .replace("_LEAVES", "")
                        .toUpperCase(Locale.ROOT);
                if (!leafFamily.equals(family) && !family.isBlank()) {
                    // Allow generic / mangrove / azalea style mismatches only when prefix matches
                    if (!leafFamily.contains(family) && !family.contains(leafFamily)) {
                        continue;
                    }
                }
                found.add(next);
                queue.add(next);
                if (found.size() >= maxLeaves) {
                    break;
                }
            }
        }
        return found;
    }

    private static long pack(Block b) {
        return (((long) b.getX() & 0x3FFFFFFL) << 38)
                | (((long) b.getZ() & 0x3FFFFFFL) << 12)
                | ((long) b.getY() & 0xFFFL);
    }
}
