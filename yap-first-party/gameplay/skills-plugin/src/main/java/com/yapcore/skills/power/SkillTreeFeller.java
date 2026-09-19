package com.yapcore.skills.power;

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

/** Connected-log BFS for the woodcutting Tree Feller ability. Logs only (VIP timber still breaks leaves). */
public final class SkillTreeFeller {

    private static final BlockFace[] NEIGHBORS = {
            BlockFace.UP, BlockFace.DOWN,
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST,
            BlockFace.NORTH_EAST, BlockFace.NORTH_WEST,
            BlockFace.SOUTH_EAST, BlockFace.SOUTH_WEST
    };

    private SkillTreeFeller() {
    }

    public static boolean isLog(Material type) {
        if (type == null) {
            return false;
        }
        try {
            return Tag.LOGS.isTagged(type);
        } catch (Throwable ignored) {
            String name = type.name();
            return name.endsWith("_LOG") || name.endsWith("_STEM") || name.endsWith("_WOOD")
                    || name.endsWith("_HYPHAE");
        }
    }

    public static String woodFamily(Material type) {
        String name = type.name();
        name = name.replace("STRIPPED_", "")
                .replace("_LOG", "")
                .replace("_WOOD", "")
                .replace("_STEM", "")
                .replace("_HYPHAE", "");
        return name.toUpperCase(Locale.ROOT);
    }

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

    private static long pack(Block b) {
        return (((long) b.getX() & 0x3FFFFFFL) << 38)
                | (((long) b.getZ() & 0x3FFFFFFL) << 12)
                | ((long) b.getY() & 0xFFFL);
    }
}
