package com.yapcore.world.schem;

import com.yapcore.world.util.BlockCodec;
import com.yapcore.world.CuboidSelection;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class SchematicIO {

    private SchematicIO() {
    }

    public static Schematic capture(CuboidSelection selection, World world) {
        List<Schematic.BlockEntry> blocks = new ArrayList<>();
        int anchorX = selection.minX();
        int anchorY = selection.minY();
        int anchorZ = selection.minZ();
        for (int x = selection.minX(); x <= selection.maxX(); x++) {
            for (int y = selection.minY(); y <= selection.maxY(); y++) {
                for (int z = selection.minZ(); z <= selection.maxZ(); z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType().isAir()) {
                        continue;
                    }
                    blocks.add(new Schematic.BlockEntry(
                            x - anchorX, y - anchorY, z - anchorZ, BlockCodec.encode(block)));
                }
            }
        }
        return new Schematic(world.getName(), anchorX, anchorY, anchorZ, blocks);
    }

    public static void save(Path file, Schematic schematic) throws IOException {
        Files.createDirectories(file.getParent());
        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            w.write("# yap-schem v1");
            w.newLine();
            w.write("world=" + schematic.world());
            w.newLine();
            w.write("anchor=" + schematic.anchorX() + "," + schematic.anchorY() + "," + schematic.anchorZ());
            w.newLine();
            for (Schematic.BlockEntry entry : schematic.blocks()) {
                w.write("block=" + entry.dx() + "," + entry.dy() + "," + entry.dz() + "," + entry.encoded());
                w.newLine();
            }
        }
    }

    public static Schematic load(Path file) throws IOException {
        String world = "world";
        int ax = 0;
        int ay = 0;
        int az = 0;
        List<Schematic.BlockEntry> blocks = new ArrayList<>();
        try (BufferedReader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                if (line.startsWith("world=")) {
                    world = line.substring("world=".length());
                } else if (line.startsWith("anchor=")) {
                    String[] parts = line.substring("anchor=".length()).split(",");
                    ax = Integer.parseInt(parts[0]);
                    ay = Integer.parseInt(parts[1]);
                    az = Integer.parseInt(parts[2]);
                } else if (line.startsWith("block=")) {
                    String[] parts = line.substring("block=".length()).split(",", 4);
                    blocks.add(new Schematic.BlockEntry(
                            Integer.parseInt(parts[0]),
                            Integer.parseInt(parts[1]),
                            Integer.parseInt(parts[2]),
                            parts[3]));
                }
            }
        }
        return new Schematic(world, ax, ay, az, blocks);
    }
}
