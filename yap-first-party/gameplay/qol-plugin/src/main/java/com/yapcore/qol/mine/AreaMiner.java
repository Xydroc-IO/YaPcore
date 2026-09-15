package com.yapcore.qol.mine;

import org.bukkit.Axis;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * Flat NxN mining plane perpendicular to the face the player mined.
 * Region: SYNC — callers must already be on the broken block's region thread
 * (or schedule each returned block via YapSched.region).
 */
public final class AreaMiner {

    private AreaMiner() {
    }

    /**
     * Collects blocks in a flat square centered on {@code origin}, excluding origin.
     * Size 3 → offsets -1..1; even sizes use a slightly asymmetric window.
     */
    public static List<Block> planeAround(Block origin, Player player, int size, int maxBlocks) {
        BlockFace face = minedFace(player, origin);
        Axis depth = depthAxis(face);
        int halfLow = size / 2;
        int halfHigh = size - halfLow - 1;
        List<Block> out = new ArrayList<>(Math.min(size * size, maxBlocks));
        int ox = origin.getX();
        int oy = origin.getY();
        int oz = origin.getZ();
        for (int a = -halfLow; a <= halfHigh; a++) {
            for (int b = -halfLow; b <= halfHigh; b++) {
                if (a == 0 && b == 0) {
                    continue;
                }
                int x = ox;
                int y = oy;
                int z = oz;
                switch (depth) {
                    case X -> {
                        y = oy + a;
                        z = oz + b;
                    }
                    case Y -> {
                        x = ox + a;
                        z = oz + b;
                    }
                    case Z -> {
                        x = ox + a;
                        y = oy + b;
                    }
                }
                Block candidate = origin.getWorld().getBlockAt(x, y, z);
                out.add(candidate);
                if (out.size() >= maxBlocks) {
                    return out;
                }
            }
        }
        return out;
    }

    static BlockFace minedFace(Player player, Block origin) {
        Vector toBlock = origin.getLocation().add(0.5, 0.5, 0.5)
                .toVector()
                .subtract(player.getEyeLocation().toVector());
        double ax = Math.abs(toBlock.getX());
        double ay = Math.abs(toBlock.getY());
        double az = Math.abs(toBlock.getZ());
        if (ay >= ax && ay >= az) {
            return toBlock.getY() >= 0 ? BlockFace.DOWN : BlockFace.UP;
        }
        if (ax >= az) {
            return toBlock.getX() >= 0 ? BlockFace.WEST : BlockFace.EAST;
        }
        return toBlock.getZ() >= 0 ? BlockFace.NORTH : BlockFace.SOUTH;
    }

    private static Axis depthAxis(BlockFace face) {
        return switch (face) {
            case UP, DOWN -> Axis.Y;
            case EAST, WEST -> Axis.X;
            default -> Axis.Z;
        };
    }
}
