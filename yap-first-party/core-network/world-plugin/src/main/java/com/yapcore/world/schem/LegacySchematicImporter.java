package com.yapcore.world.schem;

import com.yapcore.world.schem.nbt.MinimalNbt;
import com.yapcore.world.util.BlockCodec;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Imports classic MCEdit / WorldEdit {@code .schematic} (gzip NBT with Blocks + Data arrays).
 */
public final class LegacySchematicImporter {

    private LegacySchematicImporter() {
    }

    public static Schematic importFile(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            MinimalNbt.Compound root = MinimalNbt.readGzipCompound(in);
            short width = root.getShort("Width", (short) 0);
            short height = root.getShort("Height", (short) 0);
            short length = root.getShort("Length", (short) 0);
            if (width <= 0 || height <= 0 || length <= 0) {
                throw new IOException("invalid .schematic dimensions");
            }
            byte[] blocks = root.getByteArray("Blocks");
            byte[] data = root.getByteArray("Data");
            if (blocks.length < width * height * length) {
                throw new IOException("truncated Blocks array");
            }
            List<Schematic.BlockEntry> out = new ArrayList<>();
            int volume = width * height * length;
            for (int i = 0; i < volume; i++) {
                int id = blocks[i] & 0xFF;
                if (id == 0) {
                    continue;
                }
                int y = i / (width * length);
                int rem = i % (width * length);
                int z = rem / width;
                int x = rem % width;
                byte meta = i < data.length ? data[i] : 0;
                Material mat = legacyId(id);
                if (mat == null || mat.isAir()) {
                    continue;
                }
                BlockData bd = Bukkit.createBlockData(mat);
                out.add(new Schematic.BlockEntry(x, y, z, BlockCodec.encode(bd)));
            }
            return new Schematic("imported", 0, 0, 0, out);
        }
    }

    /** Best-effort classic id → Material (common blocks only). */
    private static Material legacyId(int id) {
        return switch (id) {
            case 1 -> Material.STONE;
            case 2 -> Material.GRASS_BLOCK;
            case 3 -> Material.DIRT;
            case 4 -> Material.COBBLESTONE;
            case 5 -> Material.OAK_PLANKS;
            case 7 -> Material.BEDROCK;
            case 8, 9 -> Material.WATER;
            case 10, 11 -> Material.LAVA;
            case 12 -> Material.SAND;
            case 13 -> Material.GRAVEL;
            case 14 -> Material.GOLD_ORE;
            case 15 -> Material.IRON_ORE;
            case 16 -> Material.COAL_ORE;
            case 17 -> Material.OAK_LOG;
            case 18 -> Material.OAK_LEAVES;
            case 20 -> Material.GLASS;
            case 24 -> Material.SANDSTONE;
            case 35 -> Material.WHITE_WOOL;
            case 41 -> Material.GOLD_BLOCK;
            case 42 -> Material.IRON_BLOCK;
            case 45 -> Material.BRICKS;
            case 49 -> Material.OBSIDIAN;
            case 50 -> Material.TORCH;
            case 54 -> Material.CHEST;
            case 56 -> Material.DIAMOND_ORE;
            case 57 -> Material.DIAMOND_BLOCK;
            case 58 -> Material.CRAFTING_TABLE;
            case 64 -> Material.OAK_DOOR;
            case 68 -> Material.OAK_WALL_SIGN;
            case 98 -> Material.STONE_BRICKS;
            default -> Material.STONE;
        };
    }
}
