package com.yapcore.pregen.shape;

import org.bukkit.World;
import org.bukkit.WorldBorder;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Chunks whose centers lie inside the world border. */
public final class WorldBorderShape implements ChunkShape {

    private final List<ChunkPos> coords;
    private final String desc;

    public WorldBorderShape(World world) {
        WorldBorder border = world.getWorldBorder();
        double size = border.getSize();
        double half = size / 2.0;
        double cx = border.getCenter().getX();
        double cz = border.getCenter().getZ();
        int minX = (int) Math.floor(cx - half);
        int maxX = (int) Math.floor(cx + half) - 1;
        int minZ = (int) Math.floor(cz - half);
        int maxZ = (int) Math.floor(cz + half) - 1;
        ChunkPos a = ChunkPos.fromBlock(minX, minZ);
        ChunkPos b = ChunkPos.fromBlock(maxX, maxZ);
        coords = new ArrayList<>();
        for (int x = a.x(); x <= b.x(); x++) {
            for (int z = a.z(); z <= b.z(); z++) {
                double midX = x * 16 + 8;
                double midZ = z * 16 + 8;
                double dx = midX - cx;
                double dz = midZ - cz;
                if (Math.abs(dx) <= half && Math.abs(dz) <= half) {
                    coords.add(new ChunkPos(x, z));
                }
            }
        }
        desc = "worldborder size=" + (int) size + " center=" + (int) cx + "," + (int) cz;
    }

    @Override
    public long size() {
        return coords.size();
    }

    @Override
    public String description() {
        return desc;
    }

    @Override
    public Iterator<ChunkPos> iterator() {
        return coords.iterator();
    }
}
